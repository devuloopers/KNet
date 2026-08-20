package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineInputFocusPolicyTest {
    @Test
    fun activeLineDoesNotRequestFocusDuringViewportDragSelection() {
        assertFalse(
            shouldRequestLineInputFocus(
                isActive = true,
                targetColumn = 4,
                shouldRequestFocus = true,
                isViewportSelecting = true
            )
        )
    }

    @Test
    fun focusedInputDoesNotPublishCaretDuringViewportDragSelection() {
        assertFalse(
            shouldPublishLineInputCaret(
                isFocused = true,
                isViewportSelecting = true,
                isSelectionGestureActive = false
            )
        )
        assertTrue(
            shouldPublishLineInputCaret(
                isFocused = true,
                isViewportSelecting = false,
                isSelectionGestureActive = false
            )
        )
    }

    @Test
    fun livePointerOwnershipSuppressesCaretBeforeComposeSelectionStateCatchesUp() {
        assertFalse(
            shouldPublishLineInputCaret(
                isFocused = true,
                isViewportSelecting = false,
                isSelectionGestureActive = true
            )
        )
    }
}
