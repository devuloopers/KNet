package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficRecordFilter
import com.devuloopers.knet.application.contract.traffic.TrafficFacetQuery
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.data.desktop.traffic.repository.DesktopTrafficQueryAdapter
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.capture.entity.OpaqueFlowEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpScheme
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CanonicalTrafficHistoryQueryTest {
    @Test
    fun `opaque flow mutations invalidate the desktop live Traffic generation`() = runBlocking {
        val root = Files.createTempDirectory("knet-opaque-generation-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        try {
            dao.insertSession(CaptureSessionEntity("session", 1L, null, "ACTIVE", 0L))
            dao.insertConnection(connection("session"))
            val query = DesktopTrafficQueryAdapter(dao, FileBodyStore(root.resolve("bodies")))
            val updates = Channel<com.devuloopers.knet.application.contract.traffic.TrafficGeneration>(
                capacity = Channel.UNLIMITED,
            )
            val collector = launch(Dispatchers.Default) {
                query.generations.collect { generation -> updates.send(generation) }
            }
            withTimeout(5_000L) { updates.receive() }

            dao.insertOpaqueFlow(opaqueFlow("opaque-live", "session", 2_000L))

            assertEquals("session", withTimeout(5_000L) { updates.receive() }.sessionId.value)
            collector.cancel()
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `chronological cursor pages across HTTP and opaque rows without omission`() = runTest {
        val root = Files.createTempDirectory("knet-merged-history-query-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        try {
            dao.insertSession(CaptureSessionEntity("session", 1L, null, "ACTIVE", 0L))
            dao.insertConnection(connection("session"))
            dao.insertExchange(exchange("old-http", "session", 1_000L, "https", "HTTP/1.1", "/old"))
            dao.insertOpaqueFlow(opaqueFlow("middle-opaque", "session", 2_000L))
            dao.insertExchange(exchange("new-http", "session", 3_000L, "https", "HTTP/1.1", "/new"))
            val query = CanonicalTrafficQueryAdapter(
                sessionId = null,
                dao = dao,
                bodyStore = FileBodyStore(root.resolve("bodies")),
            )

            val observed = mutableListOf<String>()
            val observedHistorySequences = mutableListOf<Long>()
            var cursor: com.devuloopers.knet.application.contract.traffic.TrafficPageCursor? = null
            do {
                val page = query.query(TrafficPageQuery(limit = 1, cursor = cursor))
                observed += page.items.map { it.exchange.id.value }
                observed += page.opaqueItems.map { it.flow.id.value }
                observedHistorySequences += page.items.map { it.historySequence.value }
                observedHistorySequences += page.opaqueItems.map { it.historySequence.value }
                assertEquals(3L, page.totalCount)
                cursor = page.nextCursor
            } while (cursor != null)

            assertEquals(listOf("new-http", "middle-opaque", "old-http"), observed)
            assertEquals(listOf(3L, 2L, 1L), observedHistorySequences)
            assertEquals(
                listOf("middle-opaque"),
                query.query(
                    TrafficPageQuery(limit = 20, recordFilter = TrafficRecordFilter.PROTECTED),
                ).opaqueItems.map { it.flow.id.value },
            )
            assertEquals(
                listOf("new-http", "old-http"),
                query.query(
                    TrafficPageQuery(limit = 20, recordFilter = TrafficRecordFilter.DECRYPTED),
                ).items.map { it.exchange.id.value },
            )
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun `global query preserves retained sessions and applies typed store filters`() = runTest {
        val root = Files.createTempDirectory("knet-history-query-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val dao = database.canonicalCaptureDao()
        try {
            listOf("old-session", "new-session").forEachIndexed { index, sessionId ->
                dao.insertSession(
                    CaptureSessionEntity(
                        id = sessionId,
                        startedAtEpochMillis = index.toLong() + 1L,
                        endedAtEpochMillis = index.toLong() + 2L,
                        state = "CLOSED",
                        version = 1L,
                    ),
                )
                dao.insertConnection(connection(sessionId))
            }
            dao.insertExchange(exchange("old", "old-session", 1_000L, "http", "HTTP/1.1", "/legacy/find-me"))
            dao.insertExchange(
                exchange(
                    id = "new",
                    sessionId = "new-session",
                    timestamp = 2_000L,
                    scheme = "https",
                    protocol = "HTTP/1.1",
                    path = "/current",
                    responseProtocol = "HTTP/2",
                ),
            )

            val query = CanonicalTrafficQueryAdapter(
                sessionId = null,
                dao = dao,
                bodyStore = FileBodyStore(root.resolve("bodies")),
            )

            val newestPage = query.query(TrafficPageQuery(limit = 1))
            assertEquals(2L, newestPage.totalCount)
            assertEquals(listOf(2L), newestPage.items.map { item -> item.captureSequence.value })
            assertEquals(listOf(2L), newestPage.items.map { item -> item.historySequence.value })
            val olderPage = query.query(
                TrafficPageQuery(limit = 1, cursor = assertNotNull(newestPage.nextCursor)),
            )
            assertEquals(2L, olderPage.totalCount)
            assertEquals(listOf(1L), olderPage.items.map { item -> item.captureSequence.value })
            assertEquals(listOf(1L), olderPage.items.map { item -> item.historySequence.value })

            assertEquals(
                listOf("new", "old"),
                query.query(TrafficPageQuery(limit = 20)).items.map { item -> item.exchange.id.value },
            )
            val filteredOld = query.query(TrafficPageQuery(limit = 20, searchContains = "find-me"))
            assertEquals(listOf("old"), filteredOld.items.map { item -> item.exchange.id.value })
            assertEquals(listOf(1L), filteredOld.items.map { item -> item.historySequence.value })

            val filteredNew = query.query(
                TrafficPageQuery(
                    limit = 20,
                    schemes = setOf(HttpScheme.fromToken("https")),
                    protocols = setOf(ApplicationProtocol.fromToken("HTTP/2")),
                ),
            )
            assertEquals(listOf("new"), filteredNew.items.map { item -> item.exchange.id.value })
            assertEquals(listOf(2L), filteredNew.items.map { item -> item.historySequence.value })

            val facets = query.queryFacets(TrafficFacetQuery())
            assertEquals(2L, facets.totalCount)
            assertEquals(1L, facets.httpCount)
            assertEquals(1L, facets.httpsCount)

            val searchedFacets = query.queryFacets(TrafficFacetQuery(searchContains = "find-me"))
            assertEquals(1L, searchedFacets.totalCount)
            assertEquals(1L, searchedFacets.httpCount)
            assertEquals(0L, searchedFacets.httpsCount)

            val http2Facets = query.queryFacets(
                TrafficFacetQuery(protocols = setOf(ApplicationProtocol.fromToken("HTTP/2"))),
            )
            assertEquals(1L, http2Facets.totalCount)
            assertEquals(0L, http2Facets.httpCount)
            assertEquals(1L, http2Facets.httpsCount)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    private fun connection(sessionId: String): TrafficConnectionEntity = TrafficConnectionEntity(
        id = "connection-$sessionId",
        sessionId = sessionId,
        sequenceVersion = 1L,
        openedAtEpochMillis = 1L,
        closedAtEpochMillis = 2L,
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

    private fun exchange(
        id: String,
        sessionId: String,
        timestamp: Long,
        scheme: String,
        protocol: String,
        path: String,
        responseProtocol: String = protocol,
    ): CanonicalExchangeEntity = CanonicalExchangeEntity(
        id = id,
        sessionId = sessionId,
        connectionId = "connection-$sessionId",
        streamId = null,
        connectionSequence = 1L,
        version = 2L,
        state = "COMPLETED",
        startedAtEpochMillis = timestamp,
        completedAtEpochMillis = timestamp + 1L,
        method = "GET",
        scheme = scheme,
        host = "api.example",
        port = null,
        pathAndQuery = path,
        protocol = protocol,
        requestHeadersEncoded = "H1:0:",
        requestBodyId = null,
        responseProtocol = responseProtocol,
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

    private fun opaqueFlow(id: String, sessionId: String, timestamp: Long): OpaqueFlowEntity = OpaqueFlowEntity(
        id = id,
        sessionId = sessionId,
        connectionId = "connection-$sessionId",
        connectionSequence = 2L,
        version = 1L,
        state = "COMPLETED",
        startedAtEpochMillis = timestamp,
        completedAtEpochMillis = timestamp + 1L,
        destinationHost = "protected.example",
        destinationPort = 443,
        serverName = "protected.example",
        transport = "TCP",
        security = "TLS",
        sourceApplicationId = null,
        policyRuleId = "protected-rule",
        uploadedBytes = 10L,
        downloadedBytes = 20L,
        terminalErrorCode = null,
    )
}
