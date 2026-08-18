package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficSortDirection
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Indexed storage baseline proving 100,000 exchanges are never loaded as one feature result. */
class CanonicalTrafficScaleBaselineTest {

    /** Builds a 100,000-row fixture and verifies filtered keyset pages remain bounded and indexed. */
    @Test
    fun `one hundred thousand exchanges page without full materialization`() = runTest {
        val root = Files.createTempDirectory("knet-canonical-scale-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        val sessionId = CaptureSessionId("scale-session")
        try {
            dao.insertSession(
                CaptureSessionEntity(
                    id = sessionId.value,
                    startedAtEpochMillis = 1L,
                    endedAtEpochMillis = 100_001L,
                    state = "CLOSED",
                    version = 1L,
                )
            )
            dao.insertConnection(connection(sessionId))

            val fixtureMillis = measureTimeMillis {
                repeat(FIXTURE_ROWS / INSERT_BATCH_SIZE) { batchIndex ->
                    val firstIndex = batchIndex * INSERT_BATCH_SIZE
                    val batch = List(INSERT_BATCH_SIZE) { offset -> exchange(sessionId, firstIndex + offset) }
                    val inserted = dao.insertExchangeBatch(batch)
                    assertTrue(inserted.all { rowId -> rowId != -1L })
                }
            }

            val queryAdapter = CanonicalTrafficQueryAdapter(
                sessionId = sessionId,
                dao = dao,
                bodyStore = FileBodyStore(root.resolve("bodies")),
            )
            var firstPage = queryAdapter.query(filteredQuery(sessionId))
            val queryMillis = measureTimeMillis {
                firstPage = queryAdapter.query(filteredQuery(sessionId))
            }

            assertEquals(PAGE_SIZE, firstPage.items.size)
            assertNotNull(firstPage.nextCursor)
            assertTrue(firstPage.items.all { snapshot -> snapshot.request.head.method.token == "POST" })
            assertTrue(firstPage.items.all { snapshot -> snapshot.response?.head?.status?.code == 201 })
            assertTrue(firstPage.items.all { snapshot ->
                assertIs<RequestTarget.Absolute>(snapshot.request.head.target).authority.host == "target.example"
            })
            assertTrue(
                queryMillis <= MAX_FILTERED_QUERY_MILLIS,
                "Filtered 100000-row keyset page took ${queryMillis}ms, above ${MAX_FILTERED_QUERY_MILLIS}ms.",
            )
            val databaseBytes = databaseStorageBytes(root)
            assertTrue(
                databaseBytes <= MAX_DATABASE_BYTES,
                "100000-row fixture exceeded the declared metadata storage baseline.",
            )

            System.out.println(
                "KNET_CANONICAL_SCALE_BASELINE rows=$FIXTURE_ROWS fixtureMs=$fixtureMillis " +
                    "filteredPageMs=$queryMillis databaseBytes=$databaseBytes pageSize=${firstPage.items.size}"
            )
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    /** Creates the one synthetic source connection used by the storage-only fixture. */
    private fun connection(sessionId: CaptureSessionId): TrafficConnectionEntity = TrafficConnectionEntity(
        id = CONNECTION_ID,
        sessionId = sessionId.value,
        sequenceVersion = FIXTURE_ROWS.toLong() + 1L,
        openedAtEpochMillis = 1L,
        closedAtEpochMillis = 100_001L,
        ingressKind = "LOCAL",
        clientIdentity = null,
        downstreamHost = null,
        downstreamPort = null,
        listenerHost = "127.0.0.1",
        listenerPort = 8080,
        transportProtocol = "tcp",
        receivedBytes = 0L,
        sentBytes = 0L,
        state = "CLOSED",
        terminalErrorCode = null,
    )

    /** Creates one metadata-only terminal exchange with predictable indexed filter columns. */
    private fun exchange(sessionId: CaptureSessionId, index: Int): CanonicalExchangeEntity {
        val matchesFilter = index % FILTER_INTERVAL == 0
        return CanonicalExchangeEntity(
            id = "scale-${index.toString().padStart(6, '0')}",
            sessionId = sessionId.value,
            connectionId = CONNECTION_ID,
            streamId = null,
            connectionSequence = index.toLong() + 1L,
            version = 3L,
            state = "COMPLETED",
            startedAtEpochMillis = index.toLong() + 1L,
            completedAtEpochMillis = index.toLong() + 2L,
            method = if (matchesFilter) "POST" else "GET",
            scheme = "https",
            host = if (matchesFilter) "target.example" else "other.example",
            port = 443,
            pathAndQuery = "/items/$index",
            protocol = "HTTP/1.1",
            requestHeadersEncoded = "H1:0:",
            requestBodyId = null,
            responseProtocol = "HTTP/1.1",
            responseStatusCode = if (matchesFilter) 201 else 200,
            responseReasonPhrase = if (matchesFilter) "Created" else "OK",
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
    }

    /** Creates the database-side host/method/status query used by the measured baseline. */
    private fun filteredQuery(sessionId: CaptureSessionId): TrafficPageQuery = TrafficPageQuery(
        sessionId = sessionId,
        limit = PAGE_SIZE,
        direction = TrafficSortDirection.NEWEST_FIRST,
        hostContains = "target.example",
        methods = setOf(HttpMethod.fromToken("POST")),
        statuses = setOf(HttpStatus(201)),
    )

    /** Includes the SQLite database, write-ahead log, and shared-memory sidecars in storage growth. */
    private fun databaseStorageBytes(root: java.io.File): Long = root.listFiles()
        ?.filter { file -> file.name.startsWith("traffic.db") }
        ?.sumOf(java.io.File::length)
        ?: 0L

    private companion object {
        private const val CONNECTION_ID = "scale-connection"
        private const val FIXTURE_ROWS = 100_000
        private const val INSERT_BATCH_SIZE = 1_000
        private const val FILTER_INTERVAL = 10
        private const val PAGE_SIZE = 100
        private const val MAX_FILTERED_QUERY_MILLIS = 5_000L
        private const val MAX_DATABASE_BYTES = 256L * 1_024L * 1_024L
    }
}
