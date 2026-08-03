package com.devuloopers.knet.ui.desktop.codeeditor

import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.CollapsedFoldState
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentLayoutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentLayoutMapTest {

    @Test
    fun `build returns 1-to-1 mapping when no folds are collapsed`() {
        val layoutMap = DocumentLayoutMap.build(totalDocumentLines = 10, collapsedFolds = emptyMap())

        assertEquals(10, layoutMap.totalDocumentLines)
        assertEquals(10, layoutMap.visibleLineCount)

        for (i in 0 until 10) {
            assertEquals(i, layoutMap.toDisplayedLine(i))
            assertEquals(i, layoutMap.toDocumentLine(i))
            assertFalse(layoutMap.isHidden(i))
        }
    }

    @Test
    fun `build translates coordinates correctly when fold is collapsed`() {
        // Document has 10 lines (0..9).
        // Fold collapsed at displayed index 2 with 3 hidden lines (lines 3, 4, 5).
        val collapsedFolds = mapOf(
            2 to CollapsedFoldState("header {", listOf("body 1", "body 2", "body 3"))
        )
        val layoutMap = DocumentLayoutMap.build(totalDocumentLines = 10, collapsedFolds = collapsedFolds)

        assertEquals(10, layoutMap.totalDocumentLines)
        assertEquals(7, layoutMap.visibleLineCount)

        // Line 0..2
        assertEquals(0, layoutMap.toDisplayedLine(0))
        assertEquals(1, layoutMap.toDisplayedLine(1))
        assertEquals(2, layoutMap.toDisplayedLine(2))

        // Line 3..5 are hidden
        assertNull(layoutMap.toDisplayedLine(3))
        assertNull(layoutMap.toDisplayedLine(4))
        assertNull(layoutMap.toDisplayedLine(5))

        assertTrue(layoutMap.isHidden(3))
        assertTrue(layoutMap.isHidden(4))
        assertTrue(layoutMap.isHidden(5))

        // Line 6..9 shift up to displayed 3..6
        assertEquals(3, layoutMap.toDisplayedLine(6))
        assertEquals(4, layoutMap.toDisplayedLine(7))
        assertEquals(5, layoutMap.toDisplayedLine(8))
        assertEquals(6, layoutMap.toDisplayedLine(9))

        // Reverse lookups (toDocumentLine)
        assertEquals(0, layoutMap.toDocumentLine(0))
        assertEquals(1, layoutMap.toDocumentLine(1))
        assertEquals(2, layoutMap.toDocumentLine(2))
        assertEquals(6, layoutMap.toDocumentLine(3))
        assertEquals(7, layoutMap.toDocumentLine(4))
        assertEquals(8, layoutMap.toDocumentLine(5))
        assertEquals(9, layoutMap.toDocumentLine(6))
    }
}
