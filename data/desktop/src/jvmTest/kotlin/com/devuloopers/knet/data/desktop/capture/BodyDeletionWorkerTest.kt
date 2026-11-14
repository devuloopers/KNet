package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.storage.capture.dao.CanonicalCaptureDao
import com.devuloopers.knet.storage.capture.entity.DeletionOutboxEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlin.test.*

/** Durable cleanup drains beyond one batch, observes every producer, and preserves cancellation/retry safety. */
class BodyDeletionWorkerTest {
    @Test fun `startup drains more than one batch without clear or another wakeup`() = runBlocking {
        Fixture().use { fixture ->
            fixture.queue(1_537)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val worker = BodyDeletionWorker(BodyDeletionReconciler(fixture.dao, fixture.store))
            try {
                val job = worker.start(scope)
                assertSame(job, worker.start(scope), "Only one consumer may start")
                eventually { fixture.dao.getDeletionWork(1).isEmpty() }
                assertEquals(1_537, fixture.store.calls.get())
            } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun `an idle worker observes committed work without an explicit caller notification`() = runBlocking {
        Fixture().use { fixture ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val worker = BodyDeletionWorker(BodyDeletionReconciler(fixture.dao, fixture.store))
            try {
                worker.start(scope)
                delay(50)
                fixture.queue(3)
                eventually { fixture.store.calls.get() == 3 && fixture.dao.getDeletionWork(1).isEmpty() }
                fixture.queue(2, offset = 3)
                eventually { fixture.store.calls.get() == 5 && fixture.dao.getDeletionWork(1).isEmpty() }
            } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun `a failed file backs off durably while hundreds of healthy files continue`() = runBlocking {
        Fixture().use { fixture ->
            fixture.queue(302)
            val now = AtomicLong(1_000)
            val reject = AtomicBoolean(true)
            val failures = AtomicInteger()
            val store = object : BodyStore by fixture.store {
                override suspend fun delete(bodyId: BodyId): BodyDeleteResult {
                    if (bodyId.value == "body-0") {
                        failures.incrementAndGet()
                        if (reject.get()) throw IOException("Synthetic filesystem failure")
                    }
                    return fixture.store.delete(bodyId)
                }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val worker = BodyDeletionWorker(BodyDeletionReconciler(fixture.dao, store)) { now.get() }
            try {
                worker.start(scope)
                eventually { fixture.dao.getDeletionWork(1_000).let { it.size == 1 && it.single().attemptCount == 1 } }
                val pending = fixture.dao.getDeletionWork(1).single()
                assertEquals(6_000L, pending.nextAttemptAtEpochMillis)
                assertEquals(301, fixture.store.calls.get())
                assertEquals(1, failures.get())
                now.set(5_999); worker.requestCleanup(); delay(30)
                assertEquals(1, failures.get(), "Wakeups must not bypass backoff")
                reject.set(false)
                now.set(6_000); worker.requestCleanup()
                eventually { fixture.dao.getDeletionWork(1).isEmpty() }
                assertEquals(2, failures.get())
            } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun `cancellation after partial file removal leaves replayable work without fake failures`() = runBlocking {
        Fixture().use { fixture ->
            fixture.queue(2)
            val entered = CompletableDeferred<Unit>()
            val blocked = object : BodyStore by fixture.store {
                override suspend fun delete(bodyId: BodyId): BodyDeleteResult {
                    if (bodyId.value == "body-1") { entered.complete(Unit); awaitCancellation() }
                    return fixture.store.delete(bodyId)
                }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val worker = BodyDeletionWorker(BodyDeletionReconciler(fixture.dao, blocked))
            worker.start(scope)
            withTimeout(5_000) { entered.await() }
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            assertEquals(2, fixture.dao.getDeletionWork(10).size)
            assertTrue(fixture.dao.getDeletionWork(10).all { it.attemptCount == 0 })
            val replay = BodyDeletionReconciler(fixture.dao, fixture.store).reconcile()
            assertEquals(1, replay.alreadyMissing)
            assertEquals(1, replay.deleted)
            assertTrue(fixture.dao.getDeletionWork(1).isEmpty())
        }
    }

    @Test fun `one database acknowledgement handles an entire successful batch`() = runBlocking {
        Fixture().use { fixture ->
            fixture.queue(1_000)
            var acknowledgements = 0
            val counted = object : CanonicalCaptureDao by fixture.dao {
                override suspend fun completeDeletions(ids: List<Long>): Int {
                    acknowledgements++
                    return fixture.dao.completeDeletions(ids)
                }
            }
            assertEquals(1_000, BodyDeletionReconciler(counted, fixture.store).reconcile(1_000).deleted)
            assertEquals(1, acknowledgements)
        }
    }

    @Test fun `failed acknowledgement after file removal converges as missing on retry`() = runBlocking {
        Fixture().use { fixture ->
            fixture.queue(5)
            val rejectedAck = object : CanonicalCaptureDao by fixture.dao {
                override suspend fun completeDeletions(ids: List<Long>): Int = throw IOException("Synthetic acknowledgement failure")
            }
            assertFailsWith<IOException> { BodyDeletionReconciler(rejectedAck, fixture.store).reconcile() }
            assertEquals(5, fixture.dao.getDeletionWork(10).size)
            assertEquals(5, BodyDeletionReconciler(fixture.dao, fixture.store).reconcile().alreadyMissing)
            assertTrue(fixture.dao.getDeletionWork(1).isEmpty())
        }
    }

    @Test fun `the background worker deletes real files across batch boundaries`() = runBlocking {
        val root = Files.createTempDirectory("knet-real-body-cleanup-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val store = com.devuloopers.knet.engine.session.FileBodyStore(root.resolve("bodies"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val operations = (0 until 257).map { index ->
                val id = BodyId("real-$index")
                store.openWrite(id, BodyWritePolicy(16)).also { writer -> writer.append(byteArrayOf(1, 2, 3)); writer.complete() }
                DeletionOutboxEntity(sessionId = "retired", bodyId = id.value, operation = "DELETE_BODY", createdAtEpochMillis = 0,
                    attemptCount = 0, lastErrorCode = null)
            }
            val dao = database.canonicalCaptureDao()
            dao.enqueueDeletions(operations)
            BodyDeletionWorker(BodyDeletionReconciler(dao, store)).start(scope)
            eventually { dao.getDeletionWork(1).isEmpty() }
            for (index in listOf(0, 256)) assertFailsWith<IllegalStateException> { store.readBody(BodyId("real-$index"), BodyRange(0, 3)) }
        } finally {
            scope.cancel(); scope.coroutineContext[Job]!!.join()
            database.close(); root.deleteRecursively()
        }
    }

    private class Fixture : AutoCloseable {
        private val root = Files.createTempDirectory("knet-body-cleanup-").toFile()
        private val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        val store = CountingStore()
        suspend fun queue(count: Int, offset: Int = 0) {
            val operations = List(count) { index -> DeletionOutboxEntity(sessionId = "closed", bodyId = "body-${index + offset}",
                operation = "DELETE_BODY", createdAtEpochMillis = 0, attemptCount = 0, lastErrorCode = null) }
            store.existing.addAll(operations.map { it.bodyId })
            dao.enqueueDeletions(operations)
        }
        override fun close() { database.close(); root.deleteRecursively() }
    }

    private class CountingStore : BodyStore {
        val calls = AtomicInteger()
        val existing = ConcurrentHashMap.newKeySet<String>()
        override suspend fun delete(bodyId: BodyId): BodyDeleteResult {
            calls.incrementAndGet()
            return if (existing.remove(bodyId.value)) BodyDeleteResult.DELETED else BodyDeleteResult.NOT_FOUND
        }
        override suspend fun openWrite(bodyId: BodyId, policy: BodyWritePolicy, contentEncoding: ContentEncoding?): BodyWriteSession = error("Unused")
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("Unused")
        override suspend fun reconcileTemporaryObjects(): Int = 0
    }

    private suspend fun eventually(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(10)
    }
}
