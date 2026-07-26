package com.devuloopers.knet

import com.devuloopers.knet.highlighter.JsonLanguageHighlighter
import com.devuloopers.knet.highlighter.ParsedJsonKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [JsonLanguageHighlighter] fold ranges and [parseJsonKeyValueLine].
 */
class CodeViewerFoldingTest {

    @Test
    fun testCalculateFoldRangesSingleObject() {
        val lines = listOf(
            "{",
            "  \"name\": \"KNet\",",
            "  \"active\": true",
            "}"
        )
        val foldRanges: Map<Int, Int> = JsonLanguageHighlighter().calculateFoldRanges(lines)
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
        val foldRanges: Map<Int, Int> = JsonLanguageHighlighter().calculateFoldRanges(lines)
        assertEquals(3, foldRanges.size)
        assertEquals(8, foldRanges[0])
        assertEquals(3, foldRanges[1])
        assertEquals(7, foldRanges[4])
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
        val foldRanges: Map<Int, Int> = JsonLanguageHighlighter().calculateFoldRanges(lines)
        val autoCollapsed = mutableSetOf<Int>()
        for (entry in foldRanges.entries) {
            if (entry.value - entry.key >= 4) {
                autoCollapsed.add(entry.key)
            }
        }
        assertTrue(0 in autoCollapsed, "5-line fold [0..5] should be auto-collapsed")
        assertTrue(6 !in autoCollapsed, "2-line fold [6..8] should NOT be auto-collapsed")
    }

    @Test
    fun testGoogleSearchSuggestEntityInfoIsParseable() {
        val line = "    \"google:entityinfo\": \"Cg0vZy8xMWZkN2pjNl9nEhRBcmdlbnRpbmUgZm9vdGJhbGxlcjK7CmRhdGE6aW1hZ2UvanBlZztiYXNlNjQs\","
        val res = JsonLanguageHighlighter().parseJsonKeyValueLine(line)
        assertNotNull(res)
        assertEquals("    \"google:entityinfo\"", res.keyPart)
        assertTrue(res.valPart.trim().startsWith("\""))
        assertTrue(res.valPart.trim().length > 60)
    }

    @Test
    fun testParseJsonKeyValueSimpleKey() {
        val line = "  \"token\": \"ya29.abc123\","
        val res = JsonLanguageHighlighter().parseJsonKeyValueLine(line)
        assertNotNull(res)
        assertEquals("  \"token\"", res.keyPart)
        assertTrue(res.valPart.trim().startsWith("\"ya29.abc123\""))
    }

    @Test
    fun testParseJsonKeyValueKeyWithColon() {
        val line = "    \"google:entityinfo\": \"Cg0vZy8xMWZkN2pj\","
        val res = JsonLanguageHighlighter().parseJsonKeyValueLine(line)
        assertNotNull(res)
        assertEquals("    \"google:entityinfo\"", res.keyPart)
        assertTrue(res.valPart.trim().startsWith("\"Cg0vZy8xMWZkN2pj\""))
    }

    @Test
    fun testParseJsonKeyValueKeyWithMultipleColons() {
        val line = "  \"Content-Type:charset:utf8\": \"application/json\","
        val res = JsonLanguageHighlighter().parseJsonKeyValueLine(line)
        assertNotNull(res)
        assertEquals("  \"Content-Type:charset:utf8\"", res.keyPart)
        assertTrue(res.valPart.trim().startsWith("\"application/json\""))
    }

    @Test
    fun testParseJsonKeyValueIndentedLine() {
        val line = "        \"nested\": true"
        val res = JsonLanguageHighlighter().parseJsonKeyValueLine(line)
        assertNotNull(res)
        assertEquals("        \"nested\"", res.keyPart)
        assertEquals(" true", res.valPart)
    }

    @Test
    fun testParseJsonKeyValueNumericValue() {
        val line = "  \"expiresIn\": 3599,"
        val res = JsonLanguageHighlighter().parseJsonKeyValueLine(line)
        assertNotNull(res)
        assertEquals("  \"expiresIn\"", res.keyPart)
        assertTrue(res.valPart.trim().startsWith("3599"))
    }

    @Test
    fun testParseJsonKeyValueBooleanValue() {
        val line = "  \"bpc\": false"
        val res = JsonLanguageHighlighter().parseJsonKeyValueLine(line)
        assertNotNull(res)
        assertEquals("  \"bpc\"", res.keyPart)
        assertEquals(" false", res.valPart)
    }

    @Test
    fun testParseJsonKeyValueNonKeyValueLines() {
        val highlighter = JsonLanguageHighlighter()
        assertNull(highlighter.parseJsonKeyValueLine("{"))
        assertNull(highlighter.parseJsonKeyValueLine("}"))
        assertNull(highlighter.parseJsonKeyValueLine("  \"standalone string\","))
        assertNull(highlighter.parseJsonKeyValueLine("  123"))
        assertNull(highlighter.parseJsonKeyValueLine("  true"))
    }
}
