package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying JSON formatting and body preparation across traffic inspection workflows.
 */
class TrafficInspectorPayloadFormattingTest {

    private val jsonFormatter = JsonBodyFormatter()

    @Test
    fun testInlineJsonArrayObjectsExpandedLineByLine() {
        val inlineJson = """{"items":[{"id":1,"name":"Rule","enabled":true},{"id":2,"name":"Cert","enabled":true}]}"""
        val formatted = jsonFormatter.prettyPrintJson(inlineJson)

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
        val formatted = jsonFormatter.prettyPrintJson(jsonWithNewlines)
        assertFalse(formatted.endsWith("\n"), "Formatted text should not end with trailing newlines")
        assertEquals(3, formatted.lines().size)
    }

    @Test
    fun testPlainTextPreservedCleanly() {
        val plainText = "Hello World\nLine 2"
        val headers = mapOf("content-type" to "text/plain")
        val formatted = BodyFormatterRegistry.prettyPrintBody(headers, plainText)
        assertEquals(plainText, formatted)
    }
}
