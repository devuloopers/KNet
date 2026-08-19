package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineSelectionBoundsTest {
    @Test
    fun selectionEndsAtExactDocumentColumnAcrossTrailingWhitespace() {
        val selection = EditorSelection(EditorPosition(0, 1), EditorPosition(0, 6))

        val bounds = requireNotNull(selection.boundsForLine(lineIndex = 0, lineLength = 9))

        assertEquals(1, bounds.startColumn)
        assertEquals(6, bounds.endColumn)
        assertFalse(bounds.includesTrailingLineBreak)
    }

    @Test
    fun emptyMiddleLinePaintsOnlyItsTrailingLineBreak() {
        val selection = EditorSelection(EditorPosition(0, 2), EditorPosition(2, 3))

        val bounds = requireNotNull(selection.boundsForLine(lineIndex = 1, lineLength = 0))

        assertTrue(bounds.includesTrailingLineBreak)
        assertTrue(bounds.hasVisibleSelection(lineLength = 0))
    }

    @Test
    fun zeroColumnEndLineDoesNotProduceTransientSelectionPaint() {
        val selection = EditorSelection(EditorPosition(0, 2), EditorPosition(2, 0))

        assertNull(selection.boundsForLine(lineIndex = 2, lineLength = 12))
    }

    @Test
    fun finalLineTextSelectionDoesNotIncludeATrailingLineBreak() {
        val selection = EditorSelection(EditorPosition(0, 2), EditorPosition(2, 3))

        val bounds = requireNotNull(selection.boundsForLine(lineIndex = 2, lineLength = 12))

        assertFalse(bounds.includesTrailingLineBreak)
        assertTrue(bounds.hasVisibleSelection(lineLength = 12))
    }
}
