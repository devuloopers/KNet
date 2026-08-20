package com.devuloopers.knet.engine.interceptor

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ResponseRebuilderTest {

    @Test
    fun testRebuildNettyResponseFromModifiedDto() {
        val original = TestFixtures.createFullHttpResponse()
        val edit = TestFixtures.createResponseEdit(
            statusCode = 404,
            headers = listOf("Server" to "MockServer"),
            body = """{"error":"not_found"}"""
        )

        val rebuilt = ResponseRebuilder.rebuild(original, edit)
        assertEquals(404, rebuilt.status().code())
        assertEquals("MockServer", rebuilt.headers().get("Server"))
        assertEquals("""{"error":"not_found"}""", rebuilt.content().toString(Charsets.UTF_8))
        assertEquals("""{"error":"not_found"}""".length.toString(), rebuilt.headers().get(HttpHeaderNames.CONTENT_LENGTH))
        rebuilt.release()
        original.release()
    }

    @Test
    fun `custom reason phrase and unchanged body survive framing normalization`() {
        val original = TestFixtures.createFullHttpResponse(body = "wire-body")
        val baseEdit = TestFixtures.createResponseEdit(
            statusCode = 299,
            headers = listOf("Transfer-Encoding" to "chunked"),
            body = null,
        )
        val edit = baseEdit.copy(
            response = baseEdit.response.copy(
                head = baseEdit.response.head.copy(reasonPhrase = "Custom Result"),
            ),
        )

        val rebuilt = ResponseRebuilder.rebuild(original, edit)

        assertEquals("Custom Result", rebuilt.status().reasonPhrase())
        assertEquals("wire-body", rebuilt.content().toString(Charsets.UTF_8))
        assertFalse(rebuilt.headers().contains(HttpHeaderNames.TRANSFER_ENCODING))
        assertEquals("9", rebuilt.headers().get(HttpHeaderNames.CONTENT_LENGTH))
        rebuilt.release()
        original.release()
    }

    @Test
    fun `body-forbidden response removes payload and conflicting framing`() {
        val original = TestFixtures.createFullHttpResponse(body = "original")
        val edit = TestFixtures.createResponseEdit(
            statusCode = 204,
            headers = listOf(
                "Content-Length" to "999",
                "Transfer-Encoding" to "chunked",
            ),
            body = "replacement",
        )

        val rebuilt = ResponseRebuilder.rebuild(original, edit)

        assertEquals(0, rebuilt.content().readableBytes())
        assertFalse(rebuilt.headers().contains(HttpHeaderNames.CONTENT_LENGTH))
        assertFalse(rebuilt.headers().contains(HttpHeaderNames.TRANSFER_ENCODING))
        rebuilt.release()
        original.release()
    }

    @Test
    fun `HEAD response preserves declared representation length without forwarding a body`() {
        val original = TestFixtures.createFullHttpResponse(body = "original")
        val edit = TestFixtures.createResponseEdit(
            headers = listOf("Content-Length" to "42"),
            body = null,
        )

        val rebuilt = ResponseRebuilder.rebuild(original, edit, requestMethod = HttpMethod.HEAD)

        assertEquals(0, rebuilt.content().readableBytes())
        assertEquals("42", rebuilt.headers().get(HttpHeaderNames.CONTENT_LENGTH))
        rebuilt.release()
        original.release()
    }
}
