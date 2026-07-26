package com.devuloopers.knet

import com.devuloopers.knet.widgets.calculateFoldRanges
import com.devuloopers.knet.widgets.parseJsonKeyValueLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [calculateFoldRanges] and [parseJsonKeyValueLine] in CodeViewerWidget.
 *
 * Tests cover:
 * - Bracket fold range detection (single and nested objects/arrays)
 * - The auto-collapse threshold rule (4+ line span)
 * - parseJsonKeyValueLine: simple keys, keys containing colons, indented lines, non-KV lines
 * - Google Search Suggest payload with google:entityinfo regression
 */
class CodeViewerFoldingTest {

    // ── calculateFoldRanges ───────────────────────────────────────────────────

    @Test
    fun testCalculateFoldRangesSingleObject() {
        val lines = listOf(
            "{",
            "  \"name\": \"KNet\",",
            "  \"active\": true",
            "}"
        )
        val foldRanges = calculateFoldRanges(lines)
        assertEquals(1, foldRanges.size)
        assertEquals(3, foldRanges[0])
    }

    @Test
    fun testCalculateFoldRangesNestedObjects() {
        val lines = listOf(
            "{",
            "  \"user\": {",
            "    \"id\": 123",
            "  },",
            "  \"items\": [",
            "    \"a\",",
            "    \"b\"",
            "  ]",
            "}"
        )
        val foldRanges = calculateFoldRanges(lines)
        assertEquals(3, foldRanges.size)
        assertEquals(8, foldRanges[0]) // Root object { to }
        assertEquals(3, foldRanges[1]) // Nested user { to }
        assertEquals(7, foldRanges[4]) // Nested items [ to ]
    }

    @Test
    fun testAutoCollapseThresholdFourOrMoreLines() {
        val lines = listOf(
            "[",           // 0
            "  \"a\",",   // 1
            "  \"b\",",   // 2
            "  \"c\",",   // 3
            "  \"d\"",    // 4
            "]",           // 5
            "{",           // 6
            "  \"x\": 1", // 7
            "}"            // 8
        )
        val foldRanges = calculateFoldRanges(lines)
        val autoCollapsed = foldRanges.filter { (start, end) -> (end - start) >= 4 }.keys.toSet()
        assertTrue(0 in autoCollapsed, "5-line fold [0..5] should be auto-collapsed")
        assertTrue(6 !in autoCollapsed, "2-line fold [6..8] should NOT be auto-collapsed")
    }

    @Test
    fun testGoogleSearchSuggestEntityInfoIsParseable() {
        val line = "    \"google:entityinfo\": \"Cg0vZy8xMWZkN2pjNl9nEhRBcmdlbnRpbmUgZm9vdGJhbGxlcjK7CmRhdGE6aW1hZ2UvanBlZztiYXNlNjQs\","
        val result = parseJsonKeyValueLine(line)
        assertNotNull(result, "parseJsonKeyValueLine must not return null for google:entityinfo")
        assertEquals("    \"google:entityinfo\"", result.keyPart,
            "keyPart must capture the full key including internal colon")
        assertTrue(result.valPart.trim().startsWith("\""),
            "valPart must start with a quote character")
        assertTrue(result.valPart.trim().length > 60,
            "valPart must be longer than 60 chars so isLongString fires")
    }

    // ── parseJsonKeyValueLine ───────────────────────────────────────────────────

    @Test
    fun testParseJsonKeyValueSimpleKey() {
        val line = "  \"token\": \"ya29.abc123\","
        val result = parseJsonKeyValueLine(line)
        assertNotNull(result)
        assertEquals("  \"token\"", result.keyPart)
        assertTrue(result.valPart.trim().startsWith("\"ya29.abc123\""))
    }

    @Test
    fun testParseJsonKeyValueKeyWithColon() {
        val line = "    \"google:entityinfo\": \"Cg0vZy8xMWZkN2pj\","
        val result = parseJsonKeyValueLine(line)
        assertNotNull(result, "Should parse a key containing a colon")
        assertEquals("    \"google:entityinfo\"", result.keyPart,
            "Key part must include the full key with its internal colon")
        assertTrue(result.valPart.trim().startsWith("\"Cg0vZy8xMWZkN2pj\""),
            "Value part should be the string after the separator")
    }

    @Test
    fun testParseJsonKeyValueKeyWithMultipleColons() {
        val line = "  \"Content-Type:charset:utf8\": \"application/json\","
        val result = parseJsonKeyValueLine(line)
        assertNotNull(result)
        assertEquals("  \"Content-Type:charset:utf8\"", result.keyPart)
        assertTrue(result.valPart.trim().startsWith("\"application/json\""))
    }

    @Test
    fun testParseJsonKeyValueIndentedLine() {
        val line = "        \"nested\": true"
        val result = parseJsonKeyValueLine(line)
        assertNotNull(result)
        assertEquals("        \"nested\"", result.keyPart)
        assertEquals(" true", result.valPart)
    }

    @Test
    fun testParseJsonKeyValueNumericValue() {
        val line = "  \"expiresIn\": 3599,"
        val result = parseJsonKeyValueLine(line)
        assertNotNull(result)
        assertEquals("  \"expiresIn\"", result.keyPart)
        assertTrue(result.valPart.trim().startsWith("3599"))
    }

    @Test
    fun testParseJsonKeyValueBooleanValue() {
        val line = "  \"bpc\": false"
        val result = parseJsonKeyValueLine(line)
        assertNotNull(result)
        assertEquals("  \"bpc\"", result.keyPart)
        assertEquals(" false", result.valPart)
    }

    @Test
    fun testParseJsonKeyValueNonKeyValueLines() {
        assertNull(parseJsonKeyValueLine("{"))
        assertNull(parseJsonKeyValueLine("}"))
        assertNull(parseJsonKeyValueLine("  \"standalone string\","))
        assertNull(parseJsonKeyValueLine("  123"))
        assertNull(parseJsonKeyValueLine("  true"))
    }
}
