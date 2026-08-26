package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.BodyWritePolicy
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.capture.entity.BodyObjectEntity
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.capture.entity.DuplexMessageEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.model.body.BodyCaptureOutcome
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for bounded canonical startup recovery and global retention. */
class CanonicalMaintenanceTest {

    /** Verifies oldest closed sessions are evicted transactionally and body files converge through the outbox. */
    @Test
    fun `retention enforces count and byte limits without touching newer sessions`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-retention-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val dao = database.canonicalCaptureDao()
        try {
            repeat(3) { index ->
                val sessionId = "retention-session-$index"
                dao.insertSession(closedSession(sessionId, index.toLong() + 1L))
                val bodyId = BodyId("retention-body-$index")
                val write = bodyStore.openWrite(bodyId, BodyWritePolicy(maximumStoredBytes = 1_024L))
                write.append(ByteArray(100) { index.toByte() })
                val body = write.complete().body
                dao.insertBody(
                    bodyEntity(
                        sessionId,
                        "exchange-$index",
                        body.id.value,
                        body.storedBytes,
                        bodyStore.storageKey(body.id).value,
                    )
                )
            }

            val result = CanonicalRetentionManager(
                dao = dao,
                deletionReconciler = BodyDeletionReconciler(dao, bodyStore),
            ).enforce(
                policy = CanonicalRetentionPolicy(
                    maximumClosedSessions = 2,
                    maximumStoredBytes = 250L,
                ),
                requestedAtEpochMillis = 100L,
            )

            assertEquals(listOf("retention-session-0"), result.evictedSessions.map { it.value })
            assertEquals(1, result.queuedBodyDeletions)
            assertEquals(1, result.deletionResult.deleted)
            assertEquals(2, result.remainingClosedSessions)
            assertEquals(200L, result.remainingStoredBytes)
            assertTrue(!result.requiresAnotherPass)
            assertNull(dao.getSession("retention-session-0"))
            assertNotNull(dao.getSession("retention-session-1"))
            assertNotNull(dao.getSession("retention-session-2"))
            assertFailsWith<IllegalStateException> {
                bodyStore.readBody(BodyId("retention-body-0"), com.devuloopers.knet.application.contract.traffic.BodyRange(0L, 1))
            }
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    /** Verifies crash-left active sessions, temporary files, and missing finalized bodies converge in bounded work. */
    @Test
    fun `startup recovery marks missing bodies unavailable and closes interrupted sessions`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-recovery-").toFile()
        val bodyRoot = root.resolve("bodies")
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(bodyRoot)
        val dao = database.canonicalCaptureDao()
        try {
            dao.insertSession(
                CaptureSessionEntity(
                    id = RECOVERY_SESSION_ID,
                    startedAtEpochMillis = 1L,
                    endedAtEpochMillis = null,
                    state = "ACTIVE",
                    version = 0L,
                )
            )
            dao.insertConnection(openConnection())
            dao.insertExchange(exchangeReferencingRequestBody(MISSING_BODY_ID).copy(state = "REQUEST_HEADERS"))
            dao.insertDuplexMessage(interruptedMessage())
            dao.insertBody(
                bodyEntity(
                    RECOVERY_SESSION_ID,
                    RECOVERY_EXCHANGE_ID,
                    MISSING_BODY_ID,
                    10L,
                    bodyStore.storageKey(BodyId(MISSING_BODY_ID)).value,
                )
            )
            val referencedWriter = bodyStore.openWrite(
                BodyId(REFERENCED_BODY_ID),
                BodyWritePolicy(maximumStoredBytes = 100L),
            )
            referencedWriter.append("referenced".toByteArray())
            val referenced = referencedWriter.complete().body
            dao.insertBody(
                bodyEntity(
                    RECOVERY_SESSION_ID,
                    RECOVERY_EXCHANGE_ID,
                    REFERENCED_BODY_ID,
                    referenced.storedBytes,
                    bodyStore.storageKey(referenced.id).value,
                )
            )
            val orphanWriter = bodyStore.openWrite(
                BodyId(ORPHAN_BODY_ID),
                BodyWritePolicy(maximumStoredBytes = 100L),
            )
            orphanWriter.append("orphan".toByteArray())
            orphanWriter.complete()
            bodyRoot.resolve("tmp").resolve("abandoned.tmp").writeBytes(byteArrayOf(1, 2, 3))

            val result = CanonicalStartupRecovery(dao, bodyStore, bodyStore).recover(
                recoveredAtEpochMillis = 50L,
                bodyBatchSize = 1,
                maximumBodiesToCheck = 1,
            )

            assertEquals(1, result.recoveredSessions)
            assertEquals(1, result.recoveredConnections)
            assertEquals(1, result.recoveredExchanges)
            assertEquals(1, result.recoveredMessages)
            assertEquals(1, result.temporaryObjectsDeleted)
            assertEquals(1, result.checkedBodies)
            assertEquals(1, result.missingBodies)
            assertEquals(0, result.failedBodyChecks)
            assertTrue(result.bodyScanHasMore)
            assertEquals(2, result.checkedStoredObjects)
            assertEquals(1, result.orphanedStoredObjectsDeleted)
            assertEquals(0, result.failedStoredObjectDeletes)
            assertTrue(!result.storedObjectScanHasMore)
            assertEquals("RECOVERED_AFTER_CRASH", dao.getSession(RECOVERY_SESSION_ID)?.state)
            val recoveredConnection = assertNotNull(dao.getConnection(RECOVERY_CONNECTION_ID))
            assertEquals("CLOSED", recoveredConnection.state)
            assertEquals("process-interrupted", recoveredConnection.terminalErrorCode)
            val recoveredExchange = assertNotNull(dao.getExchange(RECOVERY_EXCHANGE_ID))
            assertEquals("FAILED", recoveredExchange.state)
            assertEquals("process-interrupted", recoveredExchange.terminalErrorCode)
            val recoveredMessage = assertNotNull(dao.getDuplexMessage(RECOVERY_MESSAGE_ID))
            assertEquals("FAILED", recoveredMessage.state)
            assertEquals("process-interrupted", recoveredMessage.errorCode)
            val missingBody = assertNotNull(dao.getBody(MISSING_BODY_ID))
            assertEquals("MISSING", missingBody.state)
            assertEquals("FAILED:body-file-missing", missingBody.outcome)
            assertEquals(
                bodyStore.storageKey(BodyId(REFERENCED_BODY_ID)).value,
                dao.getBody(REFERENCED_BODY_ID)?.storageKey,
            )
            assertEquals(
                "referenced",
                bodyStore.readBody(BodyId(REFERENCED_BODY_ID), com.devuloopers.knet.application.contract.traffic.BodyRange(0L, 100))
                    .copyBytes().decodeToString(),
            )
            assertFailsWith<IllegalStateException> {
                bodyStore.readBody(BodyId(ORPHAN_BODY_ID), com.devuloopers.knet.application.contract.traffic.BodyRange(0L, 1))
            }

            val snapshot = CanonicalCaptureEntityMapper.snapshot(
                exchange = exchangeReferencingRequestBody(MISSING_BODY_ID),
                bodies = mapOf(MISSING_BODY_ID to missingBody),
            )
            assertIs<MessageBodyRef.Unavailable>(snapshot.request.body)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    /** Verifies same-size bit corruption is detected without exposing body paths through production APIs. */
    @Test
    fun `bounded integrity scrub marks a corrupt finalized body unavailable`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-integrity-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val dao = database.canonicalCaptureDao()
        try {
            dao.insertSession(closedSession("integrity-session", 1L))
            val bodyId = BodyId("integrity-body")
            val write = bodyStore.openWrite(bodyId, BodyWritePolicy(1_024L))
            write.append("original".encodeToByteArray())
            val body = write.complete().body
            val storageKey = bodyStore.storageKey(bodyId)
            dao.insertBody(
                bodyEntity(
                    "integrity-session",
                    "integrity-exchange",
                    bodyId.value,
                    body.storedBytes,
                    storageKey.value,
                ).copy(
                    digestAlgorithm = body.digest?.algorithm?.name,
                    digestValue = body.digest?.value,
                )
            )
            root.resolve("bodies/objects/${storageKey.value.take(2)}/${storageKey.value}.body")
                .writeText("tampered")

            val result = CanonicalBodyIntegrityVerifier(dao, bodyStore).verify()

            assertEquals(1, result.checked)
            assertEquals(1, result.corrupt)
            assertEquals(0, result.valid)
            assertEquals("CORRUPT", dao.getBody(bodyId.value)?.state)
            assertEquals("FAILED:body-integrity-digest-mismatch", dao.getBody(bodyId.value)?.outcome)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    /** Verifies a new database/process owner recovers old active rows before admitting new capture. */
    @Test
    fun `restart boundary recovers interrupted ownership before opening a new writer`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-process-restart-").toFile()
        val databaseFile = root.resolve("traffic.db")
        val firstDatabase = DatabaseFactory.create(databaseFile)
        try {
            val firstDao = firstDatabase.canonicalCaptureDao()
            firstDao.insertSession(
                CaptureSessionEntity(
                    id = RESTART_SESSION_ID,
                    startedAtEpochMillis = 1L,
                    endedAtEpochMillis = null,
                    state = "ACTIVE",
                    version = 0L,
                )
            )
            firstDao.insertConnection(
                openConnection().copy(
                    id = RESTART_CONNECTION_ID,
                    sessionId = RESTART_SESSION_ID,
                )
            )
            firstDao.insertExchange(
                exchangeReferencingRequestBody(MISSING_BODY_ID).copy(
                    id = RESTART_EXCHANGE_ID,
                    sessionId = RESTART_SESSION_ID,
                    connectionId = RESTART_CONNECTION_ID,
                    state = "WAITING_FOR_RESPONSE",
                    completedAtEpochMillis = null,
                    requestBodyId = null,
                    responseProtocol = null,
                    responseStatusCode = null,
                    responseReasonPhrase = null,
                    responseHeadersEncoded = null,
                    timingTotalMillis = null,
                )
            )
        } finally {
            firstDatabase.close()
        }

        val restartedDatabase = DatabaseFactory.create(databaseFile)
        val restartedBodyStore = FileBodyStore(root.resolve("bodies"))
        val adapter = CanonicalCaptureSessionFactory(
            database = restartedDatabase,
            bodyStore = restartedBodyStore,
            bodyStoreMaintenance = restartedBodyStore,
        ).openStreamingProxy(localListenerPort = 8_080, startedAtEpochMillis = 100L)
        try {
            val restartedDao = restartedDatabase.canonicalCaptureDao()
            assertEquals("RECOVERED_AFTER_CRASH", restartedDao.getSession(RESTART_SESSION_ID)?.state)
            assertEquals("CLOSED", restartedDao.getConnection(RESTART_CONNECTION_ID)?.state)
            assertEquals("process-interrupted", restartedDao.getConnection(RESTART_CONNECTION_ID)?.terminalErrorCode)
            assertEquals("FAILED", restartedDao.getExchange(RESTART_EXCHANGE_ID)?.state)
            assertEquals("process-interrupted", restartedDao.getExchange(RESTART_EXCHANGE_ID)?.terminalErrorCode)
            assertEquals(1, restartedDao.countActiveSessions())
            assertEquals("ACTIVE", restartedDao.getSession(adapter.sessionId.value)?.state)

            adapter.close()

            assertEquals(0, restartedDao.countActiveSessions())
            assertEquals("CLOSED", restartedDao.getSession(adapter.sessionId.value)?.state)
        } finally {
            adapter.close()
            restartedDatabase.close()
            root.deleteRecursively()
        }
    }

    /** Creates one terminal session for oldest-first retention tests. */
    private fun closedSession(id: String, startedAt: Long): CaptureSessionEntity = CaptureSessionEntity(
        id = id,
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = startedAt + 1L,
        state = "CLOSED",
        version = 1L,
    )

    /** Creates finalized body metadata without exposing a filesystem path. */
    private fun bodyEntity(
        sessionId: String,
        exchangeId: String,
        bodyId: String,
        storedBytes: Long,
        storageKey: String,
    ): BodyObjectEntity = BodyObjectEntity(
        id = bodyId,
        sessionId = sessionId,
        exchangeId = exchangeId,
        direction = "CLIENT_TO_SERVER",
        observedBytes = storedBytes,
        storedBytes = storedBytes,
        digestAlgorithm = null,
        digestValue = null,
        contentEncoding = null,
        outcome = "COMPLETE",
        state = "FINALIZED",
        createdAtEpochMillis = 1L,
        finalizedAtEpochMillis = 1L,
        storageKey = storageKey,
    )

    /** Creates one open connection left behind by the simulated interrupted process. */
    private fun openConnection(): TrafficConnectionEntity = TrafficConnectionEntity(
        id = RECOVERY_CONNECTION_ID,
        sessionId = RECOVERY_SESSION_ID,
        sequenceVersion = 0L,
        openedAtEpochMillis = 1L,
        closedAtEpochMillis = null,
        ingressKind = "LOCAL",
        clientIdentity = null,
        downstreamHost = null,
        downstreamPort = null,
        listenerHost = "127.0.0.1",
        listenerPort = 8080,
        transportProtocol = "tcp",
        receivedBytes = 0L,
        sentBytes = 0L,
        state = "OPEN",
        terminalErrorCode = null,
    )

    private fun interruptedMessage(): DuplexMessageEntity = DuplexMessageEntity(
        id = RECOVERY_MESSAGE_ID,
        sessionId = RECOVERY_SESSION_ID,
        connectionId = RECOVERY_CONNECTION_ID,
        exchangeId = RECOVERY_EXCHANGE_ID,
        streamId = 3L,
        captureSequence = 2L,
        messageSequence = 1L,
        direction = "CLIENT_TO_SERVER",
        protocol = "grpc",
        messageKind = "data",
        occurredAtEpochMillis = 2L,
        declaredBytes = 4L,
        observedBytes = 2L,
        compressed = false,
        compressionEncoding = null,
        bodyId = null,
        state = "IN_PROGRESS",
        errorCode = null,
    )

    /** Creates one canonical exchange whose request references the recovery fixture body. */
    private fun exchangeReferencingRequestBody(bodyId: String): CanonicalExchangeEntity = CanonicalExchangeEntity(
        id = RECOVERY_EXCHANGE_ID,
        sessionId = RECOVERY_SESSION_ID,
        connectionId = RECOVERY_CONNECTION_ID,
        streamId = null,
        connectionSequence = 1L,
        version = 1L,
        state = "COMPLETED",
        startedAtEpochMillis = 1L,
        completedAtEpochMillis = 2L,
        method = "GET",
        scheme = "https",
        host = "example.test",
        port = 443,
        pathAndQuery = "/",
        protocol = "HTTP/1.1",
        requestHeadersEncoded = "H1:0:",
        requestBodyId = bodyId,
        responseProtocol = "HTTP/1.1",
        responseStatusCode = 200,
        responseReasonPhrase = "OK",
        responseHeadersEncoded = "H1:0:",
        responseBodyId = null,
        timingDnsMillis = null,
        timingConnectMillis = null,
        timingTlsMillis = null,
        timingFirstByteMillis = null,
        timingDownloadMillis = null,
        timingTotalMillis = 1L,
        terminalErrorCode = null,
    )

    private companion object {
        private const val RECOVERY_SESSION_ID = "recovery-session"
        private const val RECOVERY_CONNECTION_ID = "recovery-connection"
        private const val RECOVERY_EXCHANGE_ID = "recovery-exchange"
        private const val RECOVERY_MESSAGE_ID = "recovery-message"
        private const val MISSING_BODY_ID = "missing-body"
        private const val REFERENCED_BODY_ID = "referenced-body"
        private const val ORPHAN_BODY_ID = "orphan-body"
        private const val RESTART_SESSION_ID = "restart-abandoned-session"
        private const val RESTART_CONNECTION_ID = "restart-abandoned-connection"
        private const val RESTART_EXCHANGE_ID = "restart-abandoned-exchange"
    }
}
