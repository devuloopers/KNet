package com.devuloopers.knet.ui.desktop.codeeditor.gesture

import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SelectionGestureHandlerTest {

    @Test
    fun testStandardClickAndDrag() {
        val handler = SelectionGestureHandler()
        var updatedSelection: EditorSelection? = null

        // Press at (1, 5)
        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 5,
            isShiftPressed = false,
            currentSelection = null,
            caretState = null,
            onSelectionChange = { updatedSelection = it }
        )
        assertNull(updatedSelection)

        // Drag to (4, 10)
        handler.processPointerEvent(
            targetLineIndex = 4,
            targetColIndex = 10,
            isShiftPressed = false,
            currentSelection = null,
            caretState = null,
            onSelectionChange = { updatedSelection = it }
        )
        assertNotNull(updatedSelection)
        assertEquals(1, updatedSelection!!.startLine)
        assertEquals(5, updatedSelection!!.startCol)
        assertEquals(4, updatedSelection!!.endLine)
        assertEquals(10, updatedSelection!!.endCol)
    }

    @Test
    fun testShiftClickExtendsExistingSelection() {
        val handler = SelectionGestureHandler()
        var updatedSelection: EditorSelection? = null

        val existingSelection = EditorSelection(startLine = 2, startCol = 5, endLine = 4, endCol = 10)

        // Shift + Click at (30, 12)
        handler.processPointerEvent(
            targetLineIndex = 30,
            targetColIndex = 12,
            isShiftPressed = true,
            currentSelection = existingSelection,
            caretState = null,
            onSelectionChange = { updatedSelection = it }
        )

        assertNotNull(updatedSelection)
        // Original start anchor (2, 5) must be preserved!
        assertEquals(2, updatedSelection!!.startLine)
        assertEquals(5, updatedSelection!!.startCol)
        assertEquals(30, updatedSelection!!.endLine)
        assertEquals(12, updatedSelection!!.endCol)
    }

    @Test
    fun testShiftClickExtendsFromCaret() {
        val handler = SelectionGestureHandler()
        var updatedSelection: EditorSelection? = null

        val caretState = EditorCaretState(lineIndex = 5, colIndex = 0)

        // Shift + Click at (20, 15)
        handler.processPointerEvent(
            targetLineIndex = 20,
            targetColIndex = 15,
            isShiftPressed = true,
            currentSelection = null,
            caretState = caretState,
            onSelectionChange = { updatedSelection = it }
        )

        assertNotNull(updatedSelection)
        // Caret start anchor (5, 0) must be preserved!
        assertEquals(5, updatedSelection!!.startLine)
        assertEquals(0, updatedSelection!!.startCol)
        assertEquals(20, updatedSelection!!.endLine)
        assertEquals(15, updatedSelection!!.endCol)
    }

    @Test
    fun testSingleClickWithoutDragDismissesSelection() {
        val handler = SelectionGestureHandler()
        var updatedSelection: EditorSelection? = EditorSelection(1, 0, 2, 5)

        // Click at (1, 5) without moving
        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 5,
            isShiftPressed = false,
            currentSelection = updatedSelection,
            caretState = null,
            currentTimeMs = 1000L,
            onSelectionChange = { updatedSelection = it }
        )

        // Release mouse button
        handler.processPointerRelease(isShiftPressed = false) { updatedSelection = it }

        // Selection must be dismissed (set to null)
        assertNull(updatedSelection)
    }

    @Test
    fun testDoubleClickSelectsWord() {
        val handler = SelectionGestureHandler()
        var updatedSelection: EditorSelection? = null
        val lineText = "    \"data\": {"

        // 1st click at t=1000ms
        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 6,
            lineText = lineText,
            currentTimeMs = 1000L,
            onSelectionChange = { updatedSelection = it }
        )

        handler.processPointerRelease(isShiftPressed = false) { updatedSelection = it }

        // 2nd click at t=1100ms (within 300ms window) on 'data'
        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 6,
            lineText = lineText,
            currentTimeMs = 1100L,
            onSelectionChange = { updatedSelection = it }
        )

        assertNotNull(updatedSelection)
        assertEquals(1, updatedSelection!!.startLine)
        assertEquals(5, updatedSelection!!.startCol)
        assertEquals(1, updatedSelection!!.endLine)
        assertEquals(9, updatedSelection!!.endCol)
    }

    @Test
    fun testTripleClickSelectsFullLine() {
        val handler = SelectionGestureHandler()
        var updatedSelection: EditorSelection? = null
        val lineText = "    \"data\": {"

        // 1st click
        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 6,
            lineText = lineText,
            currentTimeMs = 1000L,
            onSelectionChange = { updatedSelection = it }
        )
        handler.processPointerRelease(isShiftPressed = false) { updatedSelection = it }

        // 2nd click
        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 6,
            lineText = lineText,
            currentTimeMs = 1100L,
            onSelectionChange = { updatedSelection = it }
        )
        handler.processPointerRelease(isShiftPressed = false) { updatedSelection = it }

        // 3rd click
        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 6,
            lineText = lineText,
            currentTimeMs = 1200L,
            onSelectionChange = { updatedSelection = it }
        )

        assertNotNull(updatedSelection)
        assertEquals(1, updatedSelection!!.startLine)
        assertEquals(0, updatedSelection!!.startCol)
        assertEquals(1, updatedSelection!!.endLine)
        assertEquals(lineText.length, updatedSelection!!.endCol)
    }
}

