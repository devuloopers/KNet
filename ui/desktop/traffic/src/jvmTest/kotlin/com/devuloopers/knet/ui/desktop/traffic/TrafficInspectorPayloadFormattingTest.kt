package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.inspector.formatBodyPayload
import com.devuloopers.knet.ui.desktop.traffic.inspector.parseCookieHeader
import com.devuloopers.knet.ui.desktop.traffic.inspector.parseSetCookieHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying auto-pretty-printing and line trimming in [formatBodyPayload].
 */
class TrafficInspectorPayloadFormattingTest {

    @Test
    fun testInlineJsonArrayObjectsExpandedLineByLine() {
        val inlineJson = """{"items":[{"id":1,"name":"Rule","enabled":true},{"id":2,"name":"Cert","enabled":true}]}"""
        val formatted = formatBodyPayload("application/json", inlineJson)

        // Asserts every property key sits on its own line
        assertTrue(formatted.contains("\"id\" : 1") || formatted.contains("\"id\": 1"), "Object property 'id' should sit on its own line")
        assertTrue(formatted.contains("\"name\" : \"Rule\"") || formatted.contains("\"name\": \"Rule\""), "Object property 'name' should sit on its own line")
        assertTrue(formatted.contains("\"enabled\" : true") || formatted.contains("\"enabled\": true"), "Object property 'enabled' should sit on its own line")

        // Asserts vertical line expansion
        assertTrue(formatted.lines().size > 8, "Inline JSON array should expand vertically into > 8 lines")
    }

    @Test
    fun testTrailingNewlinesTrimmed() {
        val jsonWithNewlines = """{"status":"ok"}

"""
        val formatted = formatBodyPayload("application/json", jsonWithNewlines)
        assertFalse(formatted.endsWith("\n"), "Formatted text should not end with trailing newlines")
        assertEquals(3, formatted.lines().size)
    }

    @Test
    fun testPlainTextPreservedCleanly() {
        val plainText = "Hello World\nLine 2"
        val formatted = formatBodyPayload("text/plain", plainText)
        assertEquals(plainText, formatted)
    }

    @Test
    fun testCookieHeaderParsing() {
        val cookieHeader = "theme=dark; session_id=abc123xyz; secure_token="
        val parsed = parseCookieHeader(cookieHeader)
        assertEquals(3, parsed.size)
        assertEquals("dark", parsed["theme"])
        assertEquals("abc123xyz", parsed["session_id"])
        assertEquals("", parsed["secure_token"])
    }


    @Test
    fun testSetCookieHeaderParsing() {
        val setCookieHeader = "auth=token_str; Domain=api.knet.dev; Path=/; Max-Age=3600; Secure; HttpOnly"
        val parsed = parseSetCookieHeader(setCookieHeader)
        kotlin.test.assertNotNull(parsed)
        assertEquals("auth", parsed.name)
        assertEquals("token_str", parsed.value)
        assertEquals("api.knet.dev", parsed.domain)
        assertEquals("/", parsed.path)
        assertEquals(3600L, parsed.maxAge)
        assertTrue(parsed.secure)
        assertTrue(parsed.httpOnly)
    }
}


