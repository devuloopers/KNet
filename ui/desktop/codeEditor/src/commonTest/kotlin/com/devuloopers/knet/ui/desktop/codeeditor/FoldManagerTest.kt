package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldManager
import com.devuloopers.knet.ui.desktop.codeeditor.model.FoldRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [FoldManager] — fold calculation and visual line mapping.
 *
 * Lives in the module's own test source set for `internal` access.
 */
class FoldManagerTest {

    @Test
    fun testCalculateFoldsOnNestedJson() {
        val lines = listOf(
            "{",
            "  \"user\": {",
            "    \"id\": 100",
            "  },",
            "  \"tags\": [",
            "    \"admin\"",
            "  ]",
            "}"
        )

        FoldManager.clearCache()
        val folds = FoldManager.calculateFolds(lines)

        assertEquals(3, folds.size)

        // Sort ascending by startLine
        val sorted = folds.sortedBy { it.startLine }

        // Outer brace {…} spans lines 0-7
        assertEquals(0, sorted[0].startLine)
        assertEquals(7, sorted[0].endLine)

        // Inner user object {…} spans lines 1-3
        assertEquals(1, sorted[1].startLine)
        assertEquals(3, sorted[1].endLine)

        // Inner tags array […] spans lines 4-6
        assertEquals(4, sorted[2].startLine)
        assertEquals(6, sorted[2].endLine)
    }

    @Test
    fun testBuildVisualLineMapWithCollapsedFolds() {
        // With fold(start=1, end=3), jumping from 1 → lineIndex=3 (skip line 2)
        // Visual sequence: 0, 1, 3, 4, 5, 6, 7  → 7 items
        val totalLines = 8
        val foldRegions = listOf(
            FoldRegion(startLine = 1, endLine = 3, closingSymbol = "}")
        )
        val collapsedStartLines = setOf(1)

        val visualMap = FoldManager.buildVisualLineMap(totalLines, collapsedStartLines, foldRegions)

        // Line 2 is hidden (the fold skips from line 1's header to line 3 which remains visible)
        assertEquals(7, visualMap.size)
        assertEquals(0, visualMap[0])
        assertEquals(1, visualMap[1])
        assertEquals(3, visualMap[2]) // jumps directly to the closing brace line
        assertEquals(4, visualMap[3])
    }

    @Test
    fun testSafetyThresholdReturnEmptyFoldsForLargeDocuments() {
        val oversizedLines = (0..FoldManager.MAX_FOLD_LINE_THRESHOLD).map { "{ \"id\": $it }" }
        FoldManager.clearCache()
        val folds = FoldManager.calculateFolds(oversizedLines)
        assertTrue(folds.isEmpty(), "Documents exceeding threshold must return empty folds for safety")
    }

    @Test
    fun testSingleLineDocumentReturnsNoFolds() {
        FoldManager.clearCache()
        val folds = FoldManager.calculateFolds(listOf("{\"id\": 1}"))
        assertTrue(folds.isEmpty(), "Single-line documents have no foldable regions")
    }

    @Test
    fun testEmptyCollapsedSetReturnsIdentityMap() {
        val visualMap = FoldManager.buildVisualLineMap(
            totalLines = 5,
            collapsedStartLines = emptySet(),
            foldRegions = emptyList()
        )
        assertEquals(listOf(0, 1, 2, 3, 4), visualMap)
    }
}
