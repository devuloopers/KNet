package com.devuloopers.knet.data.desktop.capture

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficMaintenanceAdapter
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis
import kotlin.test.*

/** Real Room/SQLite distinct-body scaling, large message streams, and metadata/file completion separation. */
class TrafficClearPerformanceTest {
    @Test fun `five thousand distinct-body exchanges clear within a bounded metadata budget`() = runBlocking {
        Fixture(10_000).use { fixture ->
            val elapsed = measureTimeMillis { fixture.maintenance.clearSourceTraffic(sourceA) }
            assertTrue(elapsed < 5_000, "Indexed 5,000-exchange clear took $elapsed ms")
            assertEquals(5_000, fixture.count("traffic_exchanges"))
            assertEquals(10_000, fixture.count("body_objects"))
            assertEquals(10_000, fixture.count("deletion_outbox"))
            assertNotNull(fixture.dao.getExchange("e-9999"))
            assertNull(fixture.dao.getExchange("e-0"))
            println("KNET_CLEAR_SCALE totalExchanges=10000 removedExchanges=5000 queuedBodies=10000 metadataMillis=$elapsed")
        }
    }

    @Test fun `clear completion does not wait for a blocked filesystem deletion`() = runBlocking {
        Fixture(2).use { fixture ->
            val entered = CompletableDeferred<Unit>()
            val blocked = object : BodyStore by EmptyStore() {
                override suspend fun delete(bodyId: BodyId): BodyDeleteResult { entered.complete(Unit); awaitCancellation() }
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            BodyDeletionWorker(BodyDeletionReconciler(fixture.dao, blocked)).start(scope)
            try {
                withTimeout(2_000) { fixture.maintenance.clearSourceTraffic(sourceA) }
                withTimeout(5_000) { entered.await() }
                assertNull(fixture.dao.getExchange("e-0"))
                assertNotNull(fixture.dao.getExchange("e-1"))
                assertEquals(2, fixture.count("deletion_outbox"))
            } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun `one large message stream queues without a body-sized heap list and background drains every batch`() = runBlocking {
        Fixture(2).use { fixture ->
            fixture.addMessages(5_000)
            fixture.maintenance.clearSourceTraffic(sourceA)
            assertEquals(5_002, fixture.count("deletion_outbox"))
            val store = EmptyStore()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                BodyDeletionWorker(BodyDeletionReconciler(fixture.dao, store)).start(scope)
                withTimeout(10_000) { while (fixture.dao.getDeletionWork(1).isNotEmpty()) delay(10) }
                assertEquals(5_002, store.deleted.get())
                assertNotNull(fixture.dao.getExchange("e-1"))
                assertEquals(2, fixture.count("body_objects"))
            } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun `global clear queues large sessions entirely in SQL and preserves active capture`() = runBlocking {
        Fixture(2_000).use { fixture ->
            fixture.insertActiveExchange()
            fixture.maintenance.clearTerminalTraffic()
            assertEquals(1, fixture.count("traffic_exchanges"))
            assertNotNull(fixture.dao.getExchange("new-live"))
            assertEquals(4_000, fixture.count("deletion_outbox"))
            assertEquals("ACTIVE", fixture.dao.getSession("live")?.state)
        }
    }

    private class Fixture(count: Int) : AutoCloseable {
        private val root = Files.createTempDirectory("knet-clear-scale-").toFile()
        private val file = root.resolve("traffic.db")
        private val database = DatabaseFactory.create(file)
        val dao = database.canonicalCaptureDao()
        val maintenance = DesktopTrafficMaintenanceAdapter(database)
        init {
            runBlocking { dao.countClosedSessions() } // Open Room and validate the migrated schema first.
            sql { connection ->
                connection.execSQL("BEGIN")
                connection.execSQL("INSERT INTO capture_sessions VALUES ('closed',1,2,'CLOSED',1)")
                for (identity in listOf("a", "b")) connection.execSQL("INSERT INTO traffic_connections (id,sessionId,sequenceVersion,openedAtEpochMillis,ingressKind,clientIdentity,listenerHost,transportProtocol,receivedBytes,sentBytes,state) VALUES ('$identity','closed',1,1,'LAN_PAIRED_DEVICE','$identity','127.0.0.1','TCP',0,0,'CLOSED')")
                val sequence = "WITH RECURSIVE seq(i) AS (SELECT 0 UNION ALL SELECT i+1 FROM seq WHERE i+1<$count) "
                connection.execSQL(sequence + "INSERT INTO traffic_exchanges (id,sessionId,connectionId,connectionSequence,version,state,startedAtEpochMillis,completedAtEpochMillis,method,pathAndQuery,protocol,requestHeadersEncoded,requestBodyId,responseBodyId) SELECT 'e-'||i,'closed',CASE WHEN i<${count / 2} THEN 'a' ELSE 'b' END,i,1,'COMPLETED',1,2,'POST','/','HTTP/1.1','H1:0:','q-'||i,'r-'||i FROM seq")
                for ((prefix, direction) in listOf("q" to "CLIENT_TO_SERVER", "r" to "SERVER_TO_CLIENT")) {
                    connection.execSQL(sequence + "INSERT INTO body_objects (id,sessionId,exchangeId,direction,observedBytes,storedBytes,outcome,state,createdAtEpochMillis,finalizedAtEpochMillis,storageKey) SELECT '$prefix-'||i,'closed','e-'||i,'$direction',1,1,'COMPLETE','FINALIZED',1,2,'$prefix-'||i FROM seq")
                }
                connection.execSQL(sequence + "INSERT INTO duplex_messages (id,sessionId,connectionId,exchangeId,captureSequence,messageSequence,direction,protocol,messageKind,occurredAtEpochMillis,observedBytes,compressed,bodyId,state) SELECT 'message-'||i,'closed',CASE WHEN i<${count / 2} THEN 'a' ELSE 'b' END,'e-'||i,i,1,'SERVER_TO_CLIENT','grpc','data',1,1,0,'r-'||i,'COMPLETED' FROM seq")
                connection.execSQL("COMMIT")
            }
        }
        fun addMessages(count: Int) = sql { connection ->
            val sequence = "WITH RECURSIVE seq(i) AS (SELECT 0 UNION ALL SELECT i+1 FROM seq WHERE i+1<$count) "
            connection.execSQL("BEGIN")
            connection.execSQL(sequence + "INSERT INTO body_objects (id,sessionId,exchangeId,direction,observedBytes,storedBytes,outcome,state,createdAtEpochMillis,finalizedAtEpochMillis,storageKey) SELECT 'm-'||i,'closed','e-0','SERVER_TO_CLIENT',1,1,'COMPLETE','FINALIZED',1,2,'m-'||i FROM seq")
            connection.execSQL(sequence + "INSERT INTO duplex_messages (id,sessionId,connectionId,exchangeId,captureSequence,messageSequence,direction,protocol,messageKind,occurredAtEpochMillis,observedBytes,compressed,bodyId,state) SELECT 'stream-'||i,'closed','a','e-0',i,i+2,'SERVER_TO_CLIENT','grpc','data',1,1,0,'m-'||i,'COMPLETED' FROM seq")
            connection.execSQL("COMMIT")
        }
        fun insertActiveExchange() = sql { connection ->
            connection.execSQL("INSERT INTO capture_sessions VALUES ('live',3,NULL,'ACTIVE',0)")
            connection.execSQL("INSERT INTO traffic_connections (id,sessionId,sequenceVersion,openedAtEpochMillis,ingressKind,clientIdentity,listenerHost,transportProtocol,receivedBytes,sentBytes,state) VALUES ('live-a','live',1,3,'LAN_PAIRED_DEVICE','a','127.0.0.1','TCP',0,0,'OPEN')")
            connection.execSQL("INSERT INTO traffic_exchanges (id,sessionId,connectionId,connectionSequence,version,state,startedAtEpochMillis,method,pathAndQuery,protocol,requestHeadersEncoded) VALUES ('new-live','live','live-a',99999,1,'REQUEST_HEADERS',3,'GET','/new','HTTP/1.1','H1:0:')")
        }
        fun count(table: String): Int = sql { connection ->
            connection.prepare("SELECT COUNT(*) FROM $table").use { statement -> statement.step(); statement.getLong(0).toInt() }
        }
        private fun <T> sql(block: (androidx.sqlite.SQLiteConnection) -> T): T = BundledSQLiteDriver().open(file.absolutePath).use(block)
        override fun close() { database.close(); root.deleteRecursively() }
    }

    private open class EmptyStore : BodyStore {
        val deleted = AtomicInteger()
        override suspend fun delete(bodyId: BodyId): BodyDeleteResult { deleted.incrementAndGet(); return BodyDeleteResult.NOT_FOUND }
        override suspend fun openWrite(bodyId: BodyId, policy: BodyWritePolicy, contentEncoding: ContentEncoding?): BodyWriteSession = error("Unused")
        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk = error("Unused")
        override suspend fun reconcileTemporaryObjects(): Int = 0
    }

    private companion object { val sourceA = TrafficSource.Device(RegisteredDeviceId("a")) }
}
