package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.application.port.inspection.InspectionBody
import com.devuloopers.knet.application.port.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.port.traffic.BodyChunk
import com.devuloopers.knet.engine.protocol.inspector.sse.SseSemanticInspector
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseSemanticInspectorTest {
    @Test
    fun `parses bounded event stream into generic annotation`() = runBlocking {
        val inspector = SseSemanticInspector()
        val exchange = exchange()
        val document = inspector.inspect(
            SemanticInspectionInput(
                exchange,
                null,
                InspectionBody(
                    listOf(BodyChunk("event: update\ndata: {\"id\":1}\n\ndata: ready\n\n".encodeToByteArray(), 0L, true)),
                    false,
                ),
            ),
        )
        assertTrue(inspector.supports(exchange))
        assertEquals("Server-Sent Events (2)", document?.title)
        assertEquals("update", document?.fields?.first { it.label == "Event types" }?.value)
    }

    private fun exchange(): HttpExchangeSnapshot = HttpExchangeSnapshot(
        id = ExchangeId("sse-test"),
        request = HttpRequestSnapshot(
            RequestHead(
                HttpMethod.fromToken("GET"),
                RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("example.test"), "/events"),
                ApplicationProtocol.fromToken("HTTP/1.1"),
                emptyList(),
            ),
        ),
        response = HttpResponseSnapshot(
            ResponseHead(
                ApplicationProtocol.fromToken("HTTP/1.1"),
                HttpStatus(200),
                "OK",
                listOf(HeaderField(HeaderName("Content-Type"), "text/event-stream; charset=utf-8")),
            ),
        ),
        state = ExchangeState.COMPLETED,
        startedAtEpochMillis = 1L,
    )
}
