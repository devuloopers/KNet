package com.devuloopers.knet.widgets.editor

import com.devuloopers.knet.editor.engine.FoldManager
import com.devuloopers.knet.editor.engine.FoldRegion
import kotlin.test.Test
import kotlin.test.assertEquals

import kotlin.test.assertTrue

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

        val folds = FoldManager.calculateFolds(lines)

        assertEquals(3, folds.size)

        // Inner user object { ... } closes first on line 3
        assertEquals(1, folds[0].startLine)
        assertEquals(3, folds[0].endLine)

        // Inner tags array [ ... ] closes second on line 6
        assertEquals(4, folds[1].startLine)
        assertEquals(6, folds[1].endLine)

        // Outer brace { ... } closes last on line 7
        assertEquals(0, folds[2].startLine)
        assertEquals(7, folds[2].endLine)
    }

    @Test
    fun testBuildVisualLineMapWithCollapsedFolds() {
        val totalLines = 8
        val foldRegions = listOf(
            FoldRegion(startLine = 1, endLine = 3, closingSymbol = "}")
        )
        val collapsedStartLines = setOf(1)

        val visualMap = FoldManager.buildVisualLineMap(totalLines, collapsedStartLines, foldRegions)

        // Hidden line 2 is omitted, lines 0, 1, 3, 4, 5, 6, 7 are visible (7 lines)
        assertEquals(7, visualMap.size)
        assertEquals(0, visualMap[0])
        assertEquals(1, visualMap[1])
        assertEquals(3, visualMap[2])
    }
}




