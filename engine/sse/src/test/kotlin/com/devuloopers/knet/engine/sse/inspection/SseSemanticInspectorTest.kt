package com.devuloopers.knet.engine.sse.inspection

import com.devuloopers.knet.application.contract.inspection.InspectionBody
import com.devuloopers.knet.application.contract.inspection.SemanticInspectionInput
import com.devuloopers.knet.application.contract.traffic.BodyChunk
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.HttpExchangeSnapshot
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseSemanticInspectorTest {
    @Test
    fun `parses bounded event stream into generic annotation`() = runTest {
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
        assertEquals("update, message", document?.fields?.first { it.label == "Event types" }?.value)
    }

    @Test
    fun `record presentation distinguishes events comments and state updates without losing raw fields`() {
        val decoder = SseProtocolMessageDecoder()
        val message = com.devuloopers.knet.traffic.model.message.ProtocolMessageSnapshot(
            com.devuloopers.knet.traffic.id.ProtocolMessageId("record"), com.devuloopers.knet.traffic.id.ConnectionId("connection"),
            exchange().id, null, com.devuloopers.knet.traffic.model.message.MessageProtocolId.SSE,
            com.devuloopers.knet.traffic.model.message.ProtocolMessageKind.RECORD,
            com.devuloopers.knet.traffic.model.TrafficDirection.SERVER_TO_CLIENT, 1, 1, null, 1, false, null,
            com.devuloopers.knet.traffic.model.body.MessageBodyRef.Empty,
            com.devuloopers.knet.traffic.model.message.ProtocolMessageState.COMPLETE)
        fun decode(text: String) = decoder.decode(com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput(
            exchange(), message, text.encodeToByteArray()))!!
        val event = decode("id: 42\nevent: update\nretry: 3000\ndata: {\"ok\":true}\n\n")
        assertEquals("update", event.fields.first { it.label == "Event type" }.value)
        assertEquals("42", event.fields.first { it.label == "Record ID" }.value)
        assertEquals("3000 ms", event.fields.first { it.label == "Retry" }.value)
        assertTrue(event.text.contains("data:"))
        val comment = decode(": heartbeat\n\n")
        assertEquals("Keep-alive comment", comment.fields.first { it.label == "Record" }.value)
        assertTrue(comment.fields.none { it.label == "Event type" || it.label == "Record ID" })
        assertEquals("State update", decode("retry: 1500\n\n").fields.first { it.label == "Record" }.value)
        assertEquals(null, decoder.decode(com.devuloopers.knet.application.contract.traffic.ProtocolMessagePayloadInput(
            exchange(), message, "data: prefix".encodeToByteArray(), truncated = true)))
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
