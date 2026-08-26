package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.BodyRange
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.application.contract.traffic.BodyDeleteResult
import com.devuloopers.knet.application.contract.traffic.BodyStore
import com.devuloopers.knet.application.contract.traffic.BodyWritePolicy
import com.devuloopers.knet.application.contract.traffic.CaptureIngressHealth
import com.devuloopers.knet.application.contract.traffic.CaptureIngressLimits
import com.devuloopers.knet.application.contract.traffic.CapturePublishResult
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.capture.entity.DeletionOutboxEntity
import com.devuloopers.knet.traffic.id.BodyId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.id.ConnectionId
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.CaptureEvent
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests the inactive canonical writer's monotonic persistence, body ownership, and saturation behavior. */
class CanonicalSessionWriterTest {

    @Test
    fun `canonical writer preserves request and response trailers independently`() = runTest {
        val fixture = Fixture.create()
        try {
            val writer = fixture.openWriter(CaptureIngressLimits(16, 16L, 16L, 16))
            writer.tryPublish(connectionOpened(sequence = 0L))
            writer.tryPublish(exchangeStarted(sequence = 1L, version = 1L, streamId = StreamId(3L)))
            writer.tryPublish(
                CaptureEvent.TrailersObserved(
                    sessionId = SESSION_ID,
                    connectionId = CONNECTION_ID,
                    sequence = 2L,
                    occurredAtEpochMillis = 1_002L,
                    exchangeId = EXCHANGE_ID,
                    exchangeVersion = 2L,
                    direction = TrafficDirection.CLIENT_TO_SERVER,
                    trailers = listOf(HeaderField(HeaderName("Request-Checksum"), "request-value")),
                )
            )
            writer.tryPublish(responseObserved(sequence = 3L, version = 3L, status = 200))
            writer.tryPublish(
                CaptureEvent.TrailersObserved(
                    sessionId = SESSION_ID,
                    connectionId = CONNECTION_ID,
                    sequence = 4L,
                    occurredAtEpochMillis = 1_004L,
                    exchangeId = EXCHANGE_ID,
                    exchangeVersion = 4L,
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    trailers = listOf(
                        HeaderField(HeaderName("Response-Checksum"), "first"),
                        HeaderField(HeaderName("Response-Checksum"), "second"),
                    ),
                )
            )
            writer.flush()

            val snapshot = assertNotNull(
                CanonicalTrafficQueryAdapter(SESSION_ID, fixture.dao, fixture.bodyStore)
                    .getExchange(EXCHANGE_ID)
            )
            assertEquals("request-value", snapshot.request.trailers.single().value)
            assertEquals(listOf("first", "second"), snapshot.response?.trailers?.map(HeaderField::value))
            assertEquals(StreamId(3L), snapshot.streamId)
            writer.close()
        } finally {
            fixture.close()
        }
    }

    /** Verifies one ordered writer persists request, response, bodies, and terminal state without regression. */
    @Test
    fun `canonical writer persists a monotonic exchange with bounded bodies`() = runTest {
        val fixture = Fixture.create()
        try {
            val writer = fixture.openWriter(
                CaptureIngressLimits(
                    metadataEventsInFlight = 32,
                    bodyBytesInFlight = 32L,
                    perBodyStoredBytes = 5L,
                    maximumChunkBytes = 16,
                )
            )
            assertIs<CapturePublishResult.Accepted>(writer.tryPublish(connectionOpened(sequence = 0L)))
            assertIs<CapturePublishResult.Accepted>(writer.tryPublish(exchangeStarted(sequence = 1L, version = 1L)))
            assertIs<CapturePublishResult.Accepted>(writer.tryPublish(responseObserved(sequence = 2L, version = 2L, status = 201)))

            val requestBody = assertNotNull(
                writer.tryReserveBody(
                    connectionId = CONNECTION_ID,
                    exchangeId = EXCHANGE_ID,
                    exchangeVersion = 3L,
                    direction = TrafficDirection.CLIENT_TO_SERVER,
                    bodyId = BodyId("request-body"),
                    contentEncoding = null,
                    requestedBytes = 4,
                )
            )
            "req!".encodeToByteArray().copyInto(requestBody.writableBytes)
            assertIs<CapturePublishResult.Accepted>(requestBody.publish(3L, 1_003L, endOfBody = true))

            val responseBody = assertNotNull(
                writer.tryReserveBody(
                    connectionId = CONNECTION_ID,
                    exchangeId = EXCHANGE_ID,
                    exchangeVersion = 4L,
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    bodyId = BodyId("response-body"),
                    contentEncoding = null,
                    requestedBytes = 8,
                )
            )
            "response".encodeToByteArray().copyInto(responseBody.writableBytes)
            assertIs<CapturePublishResult.Accepted>(responseBody.publish(4L, 1_004L, endOfBody = true))

            assertIs<CapturePublishResult.Accepted>(
                writer.tryPublish(
                    CaptureEvent.ExchangeTerminated(
                        sessionId = SESSION_ID,
                        connectionId = CONNECTION_ID,
                        sequence = 5L,
                        occurredAtEpochMillis = 1_005L,
                        exchangeId = EXCHANGE_ID,
                        exchangeVersion = 5L,
                        state = ExchangeState.COMPLETED,
                    )
                )
            )
            assertIs<CapturePublishResult.Accepted>(
                writer.tryPublish(responseObserved(sequence = 6L, version = 10L, status = 500))
            )
            writer.flush()

            val exchange = assertNotNull(fixture.dao.getExchange(EXCHANGE_ID.value))
            assertEquals(5L, exchange.version)
            assertEquals("COMPLETED", exchange.state)
            assertEquals(201, exchange.responseStatusCode)
            assertEquals("request-body", exchange.requestBodyId)
            assertEquals("response-body", exchange.responseBodyId)
            assertEquals("TRUNCATED:5", fixture.dao.getBody("response-body")?.outcome)
            assertEquals(
                "respo",
                fixture.bodyStore.readBody(BodyId("response-body"), BodyRange(0L, 16)).copyBytes().decodeToString(),
            )
            val queryAdapter = CanonicalTrafficQueryAdapter(SESSION_ID, fixture.dao, fixture.bodyStore)
            val page = queryAdapter.query(
                TrafficPageQuery(
                    sessionId = SESSION_ID,
                    limit = 10,
                    searchContains = "example.test",
                    methods = setOf(HttpMethod.fromToken("GET")),
                    statuses = setOf(HttpStatus(201)),
                )
            )
            assertEquals(listOf(EXCHANGE_ID), page.items.map { item -> item.exchange.id })
            assertEquals(listOf(1L), page.items.map { item -> item.captureSequence.value })
            assertEquals(1L, page.totalCount)
            assertNotNull(queryAdapter.getExchange(EXCHANGE_ID))

            writer.close()
            assertEquals("CLOSED", fixture.dao.getSession(SESSION_ID.value)?.state)
            val clearResult = CanonicalSessionMaintenance(
                dao = fixture.dao,
                deletionReconciler = BodyDeletionReconciler(fixture.dao, fixture.bodyStore),
            ).clearClosedSession(SESSION_ID, requestedAtEpochMillis = 2_000L)
            assertEquals(2, clearResult.bodyDeletionsQueued)
            assertEquals(2, clearResult.bodyFilesDeleted)
            assertNull(fixture.dao.getSession(SESSION_ID.value))
            assertNull(fixture.dao.getExchange(EXCHANGE_ID.value))
            assertFailsWith<IllegalStateException> {
                fixture.bodyStore.readBody(BodyId("request-body"), BodyRange(0L, 1))
            }
        } finally {
            fixture.close()
        }
    }

    /** Verifies byte admission rejects before allocation and records one compact durable gap. */
    @Test
    fun `body byte saturation preserves forwarding and records a gap`() = runTest {
        val fixture = Fixture.create()
        try {
            val writer = fixture.openWriter(
                CaptureIngressLimits(
                    metadataEventsInFlight = 8,
                    bodyBytesInFlight = 4L,
                    perBodyStoredBytes = 8L,
                    maximumChunkBytes = 4,
                )
            )
            val held = assertNotNull(
                writer.tryReserveBody(
                    CONNECTION_ID,
                    EXCHANGE_ID,
                    1L,
                    TrafficDirection.CLIENT_TO_SERVER,
                    BodyId("held"),
                    null,
                    4,
                )
            )
            assertNull(
                writer.tryReserveBody(
                    CONNECTION_ID,
                    EXCHANGE_ID,
                    1L,
                    TrafficDirection.CLIENT_TO_SERVER,
                    BodyId("denied"),
                    null,
                    1,
                )
            )
            held.cancel()
            assertIs<CapturePublishResult.Accepted>(writer.tryPublish(connectionOpened(sequence = 1L)))
            writer.flush()

            assertEquals(1L, fixture.dao.countGaps(SESSION_ID.value))
            assertIs<CaptureIngressHealth.Healthy>(writer.health.value)
            assertFailsWith<IllegalStateException> { held.publish(2L, 1_002L, endOfBody = true) }
            writer.close()
        } finally {
            fixture.close()
        }
    }

    /** Simulates disk-full capture failure and proves terminal exchange metadata still converges. */
    @Test
    fun `body storage exhaustion degrades capture without losing terminal metadata`() = runTest {
        val fixture = Fixture.create()
        try {
            val writer = fixture.openWriter(
                limits = CaptureIngressLimits(16, 16L, 16L, 16),
                bodyStore = ExhaustedBodyStore,
            )
            assertIs<CapturePublishResult.Accepted>(writer.tryPublish(connectionOpened(sequence = 0L)))
            assertIs<CapturePublishResult.Accepted>(writer.tryPublish(exchangeStarted(sequence = 1L, version = 1L)))
            val reservation = assertNotNull(
                writer.tryReserveBody(
                    CONNECTION_ID,
                    EXCHANGE_ID,
                    2L,
                    TrafficDirection.CLIENT_TO_SERVER,
                    BodyId("disk-full-body"),
                    null,
                    4,
                )
            )
            "data".encodeToByteArray().copyInto(reservation.writableBytes)
            assertIs<CapturePublishResult.Accepted>(reservation.publish(2L, 1_002L, endOfBody = true))
            assertIs<CapturePublishResult.Accepted>(
                writer.tryPublish(
                    CaptureEvent.ExchangeTerminated(
                        sessionId = SESSION_ID,
                        connectionId = CONNECTION_ID,
                        sequence = 3L,
                        occurredAtEpochMillis = 1_003L,
                        exchangeId = EXCHANGE_ID,
                        exchangeVersion = 3L,
                        state = ExchangeState.COMPLETED,
                    )
                )
            )
            writer.flush()

            assertIs<CaptureIngressHealth.Degraded>(writer.health.value)
            assertEquals("COMPLETED", fixture.dao.getExchange(EXCHANGE_ID.value)?.state)
            assertNull(fixture.dao.getExchange(EXCHANGE_ID.value)?.requestBodyId)
            assertEquals(1L, fixture.dao.countGaps(SESSION_ID.value))
            writer.close()
        } finally {
            fixture.close()
        }
    }

    /** Verifies deletion-outbox work converges both existing and already-missing body files. */
    @Test
    fun `deletion reconciler converges body files and durable work`() = runTest {
        val fixture = Fixture.create()
        try {
            val storedId = BodyId("delete-me")
            fixture.bodyStore.openWrite(storedId, BodyWritePolicy(16L)).also { writer ->
                writer.append("content".encodeToByteArray())
                writer.complete()
            }
            fixture.dao.enqueueDeletion(
                DeletionOutboxEntity(
                    sessionId = SESSION_ID.value,
                    bodyId = storedId.value,
                    operation = "DELETE_BODY",
                    createdAtEpochMillis = 1_000L,
                    attemptCount = 0,
                    lastErrorCode = null,
                )
            )
            fixture.dao.enqueueDeletion(
                DeletionOutboxEntity(
                    sessionId = SESSION_ID.value,
                    bodyId = "already-missing",
                    operation = "DELETE_BODY",
                    createdAtEpochMillis = 1_001L,
                    attemptCount = 0,
                    lastErrorCode = null,
                )
            )

            val result = BodyDeletionReconciler(fixture.dao, fixture.bodyStore).reconcile()

            assertEquals(1, result.deleted)
            assertEquals(1, result.alreadyMissing)
            assertEquals(0, result.failed)
            assertEquals(emptyList(), fixture.dao.getDeletionWork(10))
            assertFailsWith<IllegalStateException> {
                fixture.bodyStore.readBody(storedId, BodyRange(0L, 1))
            }
        } finally {
            fixture.close()
        }
    }

    /** Verifies clear refuses an active writer session and leaves its metadata intact. */
    @Test
    fun `canonical clear requires a closed session`() = runTest {
        val fixture = Fixture.create()
        try {
            val writer = fixture.openWriter(
                CaptureIngressLimits(8, 8L, 8L, 8)
            )
            val maintenance = CanonicalSessionMaintenance(
                fixture.dao,
                BodyDeletionReconciler(fixture.dao, fixture.bodyStore),
            )

            assertFailsWith<IllegalStateException> {
                maintenance.clearClosedSession(SESSION_ID, requestedAtEpochMillis = 2_000L)
            }
            assertEquals("ACTIVE", fixture.dao.getSession(SESSION_ID.value)?.state)
            writer.close()
        } finally {
            fixture.close()
        }
    }

    /** Creates a deterministic connection event. */
    private fun connectionOpened(sequence: Long): CaptureEvent.ConnectionOpened = CaptureEvent.ConnectionOpened(
        sessionId = SESSION_ID,
        connectionId = CONNECTION_ID,
        sequence = sequence,
        occurredAtEpochMillis = 1_000L + sequence,
        ingress = IngressContext(IngressKind.Local),
        downstream = TrafficEndpoint("127.0.0.1", 50_000),
        localListener = TrafficEndpoint("127.0.0.1", 8080),
        transportProtocol = "tcp",
    )

    /** Creates a deterministic canonical request event. */
    private fun exchangeStarted(
        sequence: Long,
        version: Long,
        streamId: StreamId? = null,
    ): CaptureEvent.ExchangeStarted =
        CaptureEvent.ExchangeStarted(
            sessionId = SESSION_ID,
            connectionId = CONNECTION_ID,
            sequence = sequence,
            occurredAtEpochMillis = 1_000L + sequence,
            exchangeId = EXCHANGE_ID,
            exchangeVersion = version,
            streamId = streamId,
            request = RequestHead(
                method = HttpMethod.fromToken("GET"),
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("example.test", 443),
                    pathAndQuery = "/resource",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = listOf(HeaderField(HeaderName("Accept"), "text/plain")),
            ),
        )

    /** Creates deterministic response metadata. */
    private fun responseObserved(sequence: Long, version: Long, status: Int): CaptureEvent.ResponseObserved =
        CaptureEvent.ResponseObserved(
            sessionId = SESSION_ID,
            connectionId = CONNECTION_ID,
            sequence = sequence,
            occurredAtEpochMillis = 1_000L + sequence,
            exchangeId = EXCHANGE_ID,
            exchangeVersion = version,
            response = ResponseHead(
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                status = HttpStatus(status),
                reasonPhrase = "Result",
                headers = listOf(HeaderField(HeaderName("Content-Type"), "text/plain")),
            ),
        )

    /** Isolated database and file-store resources used by one writer scenario. */
    private data class Fixture(
        val root: java.io.File,
        val database: com.devuloopers.knet.storage.database.KNetDatabase,
        val bodyStore: FileBodyStore,
    ) {
        val dao = database.canonicalCaptureDao()

        /** Opens a new writer for the shared deterministic test session. */
        suspend fun openWriter(
            limits: CaptureIngressLimits,
            bodyStore: BodyStore = this.bodyStore,
        ): CanonicalSessionWriter = CanonicalSessionWriter.open(
            sessionId = SESSION_ID,
            startedAtEpochMillis = 1_000L,
            dao = dao,
            bodyStore = bodyStore,
            bodyStoreMaintenance = this.bodyStore,
            limits = limits,
        )

        /** Closes storage and removes isolated files. */
        fun close() {
            database.close()
            root.deleteRecursively()
        }

        companion object {
            /** Creates an isolated Room database and body root. */
            fun create(): Fixture {
                val root = Files.createTempDirectory("knet-canonical-writer-").toFile()
                return Fixture(
                    root = root,
                    database = DatabaseFactory.create(root.resolve("traffic.db")),
                    bodyStore = FileBodyStore(root.resolve("bodies")),
                )
            }
        }
    }

    /** Deterministic body store used to model an exhausted filesystem. */
    private object ExhaustedBodyStore : BodyStore {
        override suspend fun openWrite(
            bodyId: BodyId,
            policy: BodyWritePolicy,
            contentEncoding: com.devuloopers.knet.traffic.model.body.ContentEncoding?,
        ): com.devuloopers.knet.application.contract.traffic.BodyWriteSession {
            throw java.io.IOException("No space left on device")
        }

        override suspend fun readBody(bodyId: BodyId, range: BodyRange): BodyChunk =
            error("No body is readable from an exhausted store.")

        override suspend fun delete(bodyId: BodyId): BodyDeleteResult = BodyDeleteResult.NOT_FOUND

        override suspend fun reconcileTemporaryObjects(): Int = 0
    }

    private companion object {
        val SESSION_ID = CaptureSessionId("session-test")
        val CONNECTION_ID = ConnectionId("connection-test")
        val EXCHANGE_ID = ExchangeId("exchange-test")
    }
}
