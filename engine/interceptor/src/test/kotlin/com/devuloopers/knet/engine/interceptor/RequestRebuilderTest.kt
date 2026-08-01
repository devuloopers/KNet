package com.devuloopers.knet.engine.interceptor

import io.netty.handler.codec.http.HttpHeaderNames
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestRebuilderTest {

    @Test
    fun testRebuildNettyRequestFromModifiedDto() {
        val original = TestFixtures.createFullHttpRequest("https://api.example.com/v1/users", body = "original")
        val modifiedDto = TestFixtures.createHttpRequestDto(
            url = "https://api.example.com/v1/users?edited=true",
            method = "POST",
            headers = listOf("X-Edited" to "true"),
            body = """{"edited":true}"""
        )

        val rebuilt = RequestRebuilder.rebuild(original, modifiedDto)
        assertEquals("POST", rebuilt.method().name())
        assertEquals("/v1/users?edited=true", rebuilt.uri())
        assertEquals("true", rebuilt.headers().get("X-Edited"))
        assertEquals("""{"edited":true}""", rebuilt.content().toString(Charsets.UTF_8))
        assertEquals("""{"edited":true}""".length.toString(), rebuilt.headers().get(HttpHeaderNames.CONTENT_LENGTH))
    }
}
