package com.devuloopers.knet.data.desktop.inspection

import com.devuloopers.knet.application.coordinator.inspection.SemanticInspectionScheduler
import com.devuloopers.knet.data.desktop.capture.CanonicalCaptureSessionFactory
import com.devuloopers.knet.data.desktop.capture.CanonicalTrafficQueryAdapter
import com.devuloopers.knet.data.desktop.capture.recordTestProxyExchange
import com.devuloopers.knet.engine.sse.inspection.SseSemanticInspector
import com.devuloopers.knet.engine.session.FileBodyStore
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.inspection.InspectionAnnotationState
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.http.*
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SseSemanticInspectionEndToEndTest {
    @Test
    fun `captured event stream becomes durable generic annotation`() = runTest {
        val root = Files.createTempDirectory("knet-sse-e2e-").toFile()
        val database = DatabaseFactory.create(root.resolve("traffic.db"))
        val bodyStore = FileBodyStore(root.resolve("bodies"))
        val session = CanonicalCaptureSessionFactory(database, bodyStore, bodyStore)
            .openStreamingProxy(localListenerPort = 8_080, startedAtEpochMillis = 1L)
        val exchangeId = ExchangeId("sse-e2e-exchange")
        try {
            session.recordTestProxyExchange(
                exchangeId = exchangeId,
                request = RequestHead(
                    HttpMethod.fromToken("GET"),
                    RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("events.test"), "/stream"),
                    ApplicationProtocol.fromToken("HTTP/1.1"),
                    emptyList(),
                ),
                response = ResponseHead(
                    ApplicationProtocol.fromToken("HTTP/1.1"),
                    HttpStatus(200),
                    "OK",
                    listOf(HeaderField(HeaderName("Content-Type"), "text/event-stream")),
                ),
                responseBody = "event: update\ndata: ready\n\n".encodeToByteArray(),
                outcome = ExchangeTerminalOutcome.Completed,
                startedAtEpochMillis = 10L,
                completedAtEpochMillis = 20L,
            )
            val query = CanonicalTrafficQueryAdapter(session.sessionId, database.canonicalCaptureDao(), bodyStore)
            val annotations = RoomInspectionAnnotationAdapter(database.canonicalCaptureDao())
            SemanticInspectionScheduler(query, annotations, listOf(SseSemanticInspector()))
                .inspect(session.sessionId, exchangeId, 30L)

            val annotation = annotations.get(exchangeId).single()
            assertEquals(InspectionAnnotationState.COMPLETED, annotation.state)
            assertEquals("sse", annotation.document?.kind)
        } finally {
            session.close()
            database.close()
            root.deleteRecursively()
        }
    }
}
