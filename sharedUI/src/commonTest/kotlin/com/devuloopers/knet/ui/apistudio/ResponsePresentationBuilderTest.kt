package com.devuloopers.knet.ui.apistudio

import com.devuloopers.knet.bodyformatter.model.BodyFormat
import com.devuloopers.knet.ui.apistudio.model.ResponsePresentationBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ResponsePresentationBuilderTest {

    @Test
    fun testJsonPayloadPreFormatting() = runTest {
        val headers = mapOf("Content-Type" to "application/json")
        val rawJson = """{"status":"ok","code":200}"""

        val presentation = ResponsePresentationBuilder.build(headers, rawJson)

        assertTrue(presentation.bodyFormat is BodyFormat.Json, "Should resolve to BodyFormat.Json")
        assertTrue(presentation.formattedBody.contains("\n"), "Formatted JSON should contain newlines")
        assertEquals(rawJson, presentation.rawBody)
        assertTrue(presentation.lineCount > 1, "Line count should be greater than 1")
    }

    @Test
    fun testCookieHeaderExtraction() = runTest {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Set-Cookie" to "session_id=xyz123; Domain=api.example.com; Path=/; Secure; HttpOnly"
        )
        val body = """{"user":"test"}"""

        val presentation = ResponsePresentationBuilder.build(headers, body)

        assertEquals(1, presentation.cookies.size)
        val cookie = presentation.cookies.first()
        assertEquals("session_id", cookie.name)
        assertEquals("xyz123", cookie.value)
        assertEquals("api.example.com", cookie.domain)
        assertEquals("/", cookie.path)
        assertTrue(cookie.isSecure)
        assertTrue(cookie.isHttpOnly)
    }

    @Test
    fun testFallbackOnEmptyBody() = runTest {
        val headers = emptyMap<String, String>()
        val body = ""

        val presentation = ResponsePresentationBuilder.build(headers, body)

        assertEquals("", presentation.formattedBody)
        assertEquals(0, presentation.lineCount)
        assertEquals(0, presentation.characterCount)
        assertTrue(presentation.cookies.isEmpty())
    }
}
