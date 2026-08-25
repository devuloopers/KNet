package com.devuloopers.knet.engine.sse.apistudio

import com.devuloopers.knet.application.port.apistudio.HttpLiveResponseUpdate
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SseHttpResponseStreamInterpreterTest {
    @Test
    fun `recognizes only identity encoded event streams`() {
        val interpreter = SseHttpResponseStreamInterpreter()

        assertTrue(interpreter.supports(head()))
        assertTrue(interpreter.supports(head(mapOf("CONTENT-TYPE" to "text/event-stream", "Content-Encoding" to "identity"))))
        assertFalse(interpreter.supports(head(mapOf("Content-Type" to "text/event-stream", "Content-Encoding" to "gzip"))))
        assertFalse(interpreter.supports(head(mapOf("Content-Type" to "application/json"))))
    }

    @Test
    fun `arbitrary chunks emit ordered protocol neutral records and gaps`() {
        val interpreter = SseHttpResponseStreamInterpreter(
            SseLimits(maximumLineBytes = 24, maximumRecordBytes = 48, maximumDataCharacters = 24),
        )
        val session = interpreter.open(head())

        val updates = buildList {
            addAll(session.accept("id: 7\nevent: pr".encodeToByteArray()))
            addAll(session.accept("ice\ndata: first\n\n: ping\n\n".encodeToByteArray()))
            addAll(session.accept("data: 1234567890123456789012345\n\n".encodeToByteArray()))
            addAll(session.finish())
        }

        val event = assertIs<HttpLiveResponseUpdate.Record>(updates[0]).value
        assertEquals(1L, event.sequence)
        assertEquals("price", event.title)
        assertEquals("first", event.data)
        assertEquals("7", event.attributes.first { it.first == "ID" }.second)
        assertEquals("Keep-alive comment", assertIs<HttpLiveResponseUpdate.Record>(updates[1]).value.title)
        assertIs<HttpLiveResponseUpdate.Gap>(updates[2])
    }

    private fun head(
        headers: Map<String, String> = mapOf("Content-Type" to "text/event-stream; charset=utf-8"),
    ): HttpExecutionResponseHead = HttpExecutionResponseHead(
        statusCode = 200,
        statusText = "OK",
        headers = headers,
        cookies = emptyMap(),
        protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
    )
}
