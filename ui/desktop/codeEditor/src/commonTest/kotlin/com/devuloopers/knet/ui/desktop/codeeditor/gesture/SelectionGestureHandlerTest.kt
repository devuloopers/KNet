package com.devuloopers.knet.ui.desktop.codeeditor.gesture

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionGestureHandlerTest {
    @Test
    fun dragPreservesDirectionalAnchorAndActiveEndpoint() {
        val handler = SelectionGestureHandler()
        var selection: EditorSelection? = null

        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 5,
            caret = EditorPosition(1, 5),
            currentTimeMs = 1_000L,
            onSelectionChange = { selection = it }
        )
        handler.processPointerEvent(
            targetLineIndex = 4,
            targetColIndex = 10,
            caret = EditorPosition(1, 5),
            currentTimeMs = 1_001L,
            onSelectionChange = { selection = it }
        )

        assertEquals(
            EditorSelection(EditorPosition(1, 5), EditorPosition(4, 10)),
            selection
        )
    }

    @Test
    fun shiftClickPreservesExistingSelectionAnchor() {
        val handler = SelectionGestureHandler()
        val existing = EditorSelection(EditorPosition(4, 10), EditorPosition(2, 5))
        var selection: EditorSelection? = null

        handler.processPointerEvent(
            targetLineIndex = 30,
            targetColIndex = 12,
            isShiftPressed = true,
            currentSelection = existing,
            caret = existing.active,
            currentTimeMs = 1_000L,
            onSelectionChange = { selection = it }
        )

        assertEquals(
            EditorSelection(EditorPosition(4, 10), EditorPosition(30, 12)),
            selection
        )
    }

    @Test
    fun shiftClickStartsAtCaretWhenNoSelectionExists() {
        val handler = SelectionGestureHandler()
        var selection: EditorSelection? = null

        handler.processPointerEvent(
            targetLineIndex = 20,
            targetColIndex = 15,
            isShiftPressed = true,
            caret = EditorPosition(5, 0),
            currentTimeMs = 1_000L,
            onSelectionChange = { selection = it }
        )

        assertEquals(
            EditorSelection(EditorPosition(5, 0), EditorPosition(20, 15)),
            selection
        )
    }

    @Test
    fun singleClickReleaseClearsSelection() {
        val handler = SelectionGestureHandler()
        var selection: EditorSelection? = EditorSelection(EditorPosition(1, 0), EditorPosition(2, 5))

        handler.processPointerEvent(
            targetLineIndex = 1,
            targetColIndex = 5,
            currentSelection = selection,
            caret = EditorPosition(2, 5),
            currentTimeMs = 1_000L,
            onSelectionChange = { selection = it }
        )
        handler.processPointerRelease(onSelectionChange = { selection = it })

        assertNull(selection)
    }

    @Test
    fun doubleClickSelectsWordAndTripleClickSelectsLine() {
        val handler = SelectionGestureHandler()
        val lineText = "    \"data\": {"
        var selection: EditorSelection? = null

        fun click(time: Long) {
            handler.processPointerEvent(
                targetLineIndex = 1,
                targetColIndex = 6,
                lineText = lineText,
                caret = EditorPosition(1, 6),
                currentTimeMs = time,
                onSelectionChange = { selection = it }
            )
        }

        click(1_000L)
        handler.processPointerRelease(onSelectionChange = { selection = it })
        click(1_100L)
        assertEquals(
            EditorSelection(EditorPosition(1, 5), EditorPosition(1, 9)),
            selection
        )

        handler.processPointerRelease(onSelectionChange = { selection = it })
        click(1_200L)
        assertEquals(
            EditorSelection(EditorPosition(1, 0), EditorPosition(1, lineText.length)),
            selection
        )
    }
}
