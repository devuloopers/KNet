package com.devuloopers.knet.data.desktop.capture

import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.capture.entity.CanonicalExchangeEntity
import com.devuloopers.knet.storage.capture.entity.CaptureSessionEntity
import com.devuloopers.knet.storage.capture.entity.TrafficConnectionEntity
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpScheme
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CanonicalTrafficHistoryQueryTest {
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
            dao.insertExchange(exchange("new", "new-session", 2_000L, "https", "HTTP/2", "/current"))

            val query = CanonicalTrafficQueryAdapter(
                sessionId = null,
                dao = dao,
                bodyStore = FileBodyStore(root.resolve("bodies")),
            )

            val newestPage = query.query(TrafficPageQuery(limit = 1))
            assertEquals(2L, newestPage.totalCount)
            assertEquals(listOf(2L), newestPage.items.map { item -> item.captureSequence.value })
            val olderPage = query.query(
                TrafficPageQuery(limit = 1, cursor = assertNotNull(newestPage.nextCursor)),
            )
            assertEquals(2L, olderPage.totalCount)
            assertEquals(listOf(1L), olderPage.items.map { item -> item.captureSequence.value })

            assertEquals(
                listOf("new", "old"),
                query.query(TrafficPageQuery(limit = 20)).items.map { item -> item.exchange.id.value },
            )
            assertEquals(
                listOf("old"),
                query.query(TrafficPageQuery(limit = 20, searchContains = "find-me"))
                    .items
                    .map { item -> item.exchange.id.value },
            )
            assertEquals(
                listOf("new"),
                query.query(
                    TrafficPageQuery(
                        limit = 20,
                        schemes = setOf(HttpScheme.fromToken("https")),
                        protocols = setOf(ApplicationProtocol.fromToken("HTTP/2")),
                    ),
                ).items.map { item -> item.exchange.id.value },
            )
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
        responseProtocol = protocol,
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
}
