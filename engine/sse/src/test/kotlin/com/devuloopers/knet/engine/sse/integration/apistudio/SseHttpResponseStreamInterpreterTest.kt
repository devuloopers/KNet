package com.devuloopers.knet.engine.sse.integration.apistudio

import com.devuloopers.knet.application.contract.apistudio.HttpLiveResponseUpdate
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
import com.devuloopers.knet.engine.sse.protocol.SseLimits
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SseHttpResponseStreamInterpreterTest {
    @Test
    fun `recognizes event streams independently from supported representation encoding`() {
        val interpreter = SseHttpResponseStreamInterpreter()

        assertTrue(interpreter.supports(head()))
        assertTrue(interpreter.supports(head(mapOf("CONTENT-TYPE" to "text/event-stream", "Content-Encoding" to "identity"))))
        assertTrue(interpreter.supports(head(mapOf("Content-Type" to "text/event-stream", "Content-Encoding" to "gzip"))))
        assertTrue(interpreter.supports(head(mapOf("Content-Type" to "text/event-stream", "Content-Encoding" to "br"))))
        assertFalse(interpreter.supports(head(mapOf("Content-Type" to "application/json"))))
    }

    @Test
    fun `gzip and deflate streams use the same ordered semantic interpreter`() {
        val source = "event: price\ndata: 42\n\n".encodeToByteArray()
        val encoded = listOf(
            "gzip" to compressGzip(source),
            "deflate" to compressDeflate(source),
        )

        encoded.forEach { (encoding, bytes) ->
            val session = SseHttpResponseStreamInterpreter().open(
                head(mapOf("Content-Type" to "text/event-stream", "Content-Encoding" to encoding)),
            )
            val updates = buildList {
                bytes.forEach { byte -> addAll(session.accept(byteArrayOf(byte))) }
                addAll(session.finish())
            }

            val record = assertIs<HttpLiveResponseUpdate.Record>(updates.single()).value
            assertEquals("price", record.title)
            assertEquals("42", record.data)
        }
    }

    @Test
    fun `unsupported encoding emits one explicit gap`() {
        val session = SseHttpResponseStreamInterpreter().open(
            head(mapOf("Content-Type" to "text/event-stream", "Content-Encoding" to "br")),
        )

        val first = session.accept(byteArrayOf(1, 2, 3))
        val second = session.accept(byteArrayOf(4))

        assertEquals("sse_content_encoding_unsupported", assertIs<HttpLiveResponseUpdate.Gap>(first.single()).reason)
        assertTrue(second.isEmpty())
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

    private fun compressGzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { stream -> stream.write(bytes) }
        output.toByteArray()
    }

    private fun compressDeflate(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        DeflaterOutputStream(output).use { stream -> stream.write(bytes) }
        output.toByteArray()
    }
}
