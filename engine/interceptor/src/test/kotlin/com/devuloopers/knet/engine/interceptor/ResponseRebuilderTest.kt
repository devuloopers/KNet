package com.devuloopers.knet.engine.interceptor

import io.netty.handler.codec.http.HttpHeaderNames
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseRebuilderTest {

    @Test
    fun testRebuildNettyResponseFromModifiedDto() {
        val original = TestFixtures.createFullHttpResponse()
        val modifiedDto = TestFixtures.createHttpResponseDto(
            statusCode = 404,
            headers = listOf("Server" to "MockServer"),
            body = """{"error":"not_found"}"""
        )

        val rebuilt = ResponseRebuilder.rebuild(original, modifiedDto)
        assertEquals(404, rebuilt.status().code())
        assertEquals("MockServer", rebuilt.headers().get("Server"))
        assertEquals("""{"error":"not_found"}""", rebuilt.content().toString(Charsets.UTF_8))
        assertEquals("""{"error":"not_found"}""".length.toString(), rebuilt.headers().get(HttpHeaderNames.CONTENT_LENGTH))
    }
}
