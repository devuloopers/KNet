package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.syntax.language.JsonLanguageHighlighter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [JsonLanguageHighlighter] fold range calculation.
 *
 * Covers single-object, nested, and threshold-triggered auto-collapse scenarios.
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
            if (entry.value - entry.key >= 4) autoCollapsed.add(entry.key)
        }
        assertTrue(0 in autoCollapsed, "5-line fold [0..5] should be auto-collapsed")
        assertTrue(6 !in autoCollapsed, "2-line fold [6..8] should NOT be auto-collapsed")
    }

    @Test
    fun testHighlightLineReturnsAnnotatedString() {
        val highlighter = JsonLanguageHighlighter()
        val result = highlighter.highlightLine("  \"name\": \"KNet\"")
        assertEquals("  \"name\": \"KNet\"", result.text)
    }

    @Test
    fun testHighlightLineHandlesBooleanAndNull() {
        val highlighter = JsonLanguageHighlighter()
        val trueResult = highlighter.highlightLine("  \"active\": true")
        val nullResult = highlighter.highlightLine("  \"data\": null")
        assertEquals("  \"active\": true", trueResult.text)
        assertEquals("  \"data\": null", nullResult.text)
    }
}
