package com.devuloopers.knet.engine.interceptor

import io.netty.handler.codec.http.HttpHeaderNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RequestRebuilderTest {

    @Test
    fun testRebuildNettyRequestFromModifiedDto() {
        val original = TestFixtures.createFullHttpRequest("https://api.example.com/v1/users", body = "original")
        val edit = TestFixtures.createRequestEdit(
            url = "https://api.example.com/v1/users?edited=true",
            method = "POST",
            headers = listOf("X-Edited" to "true"),
            body = """{"edited":true}"""
        )

        val rebuilt = RequestRebuilder.rebuild(original, edit)
        assertEquals("POST", rebuilt.method().name())
        assertEquals("/v1/users?edited=true", rebuilt.uri())
        assertEquals("true", rebuilt.headers().get("X-Edited"))
        assertEquals("""{"edited":true}""", rebuilt.content().toString(Charsets.UTF_8))
        assertEquals("""{"edited":true}""".length.toString(), rebuilt.headers().get(HttpHeaderNames.CONTENT_LENGTH))
        rebuilt.release()
        original.release()
    }

    @Test
    fun `unchanged body preserves bytes and trailers while normalizing framing`() {
        val original = TestFixtures.createFullHttpRequest(body = "compressed-wire-bytes")
        original.trailingHeaders().set("X-Checksum", "stable")
        val edit = TestFixtures.createRequestEdit(
            method = "POST",
            headers = listOf(
                "Content-Encoding" to "gzip",
                "Transfer-Encoding" to "chunked",
                "Content-Length" to "999",
            ),
            body = null,
        )

        val rebuilt = RequestRebuilder.rebuild(original, edit)

        assertEquals("compressed-wire-bytes", rebuilt.content().toString(Charsets.UTF_8))
        assertEquals("gzip", rebuilt.headers().get(HttpHeaderNames.CONTENT_ENCODING))
        assertEquals("chunked", rebuilt.headers().get(HttpHeaderNames.TRANSFER_ENCODING))
        assertFalse(rebuilt.headers().contains(HttpHeaderNames.CONTENT_LENGTH))
        assertEquals("X-Checksum", rebuilt.headers().get(HttpHeaderNames.TRAILER))
        assertEquals("stable", rebuilt.trailingHeaders().get("X-Checksum"))
        rebuilt.release()
        original.release()
    }
}
