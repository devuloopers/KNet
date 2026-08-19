package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPointerDragOwnershipTest {
    @Test
    fun textDragRemainsTextOwnedAfterEnteringBottomScrollbarZone() {
        val ownership = EditorPointerDragOwnership()

        assertEquals(EditorPointerDragOwner.Text, ownership.update(true, false))
        assertEquals(EditorPointerDragOwner.Text, ownership.update(true, true))
    }

    @Test
    fun scrollbarDragRemainsScrollbarOwnedAfterLeavingScrollbarZone() {
        val ownership = EditorPointerDragOwnership()

        assertEquals(EditorPointerDragOwner.Scrollbar, ownership.update(true, true))
        assertEquals(EditorPointerDragOwner.Scrollbar, ownership.update(true, false))
    }

    @Test
    fun releaseAllowsTheNextGestureToChooseANewOwner() {
        val ownership = EditorPointerDragOwnership()

        assertEquals(EditorPointerDragOwner.Text, ownership.update(true, false))
        assertEquals(EditorPointerDragOwner.None, ownership.update(false, true))
        assertEquals(EditorPointerDragOwner.Scrollbar, ownership.update(true, true))
    }

    @Test
    fun wrappedViewportDoesNotReserveANonexistentBottomScrollbarZone() {
        assertFalse(
            isPointerOverEditorScrollbar(
                pointerX = 100f,
                pointerY = 195f,
                containerWidth = 300f,
                containerHeight = 200f,
                scrollbarHitZoneWidth = 12f,
                hasHorizontalScrollbar = false
            )
        )
    }

    @Test
    fun renderedScrollbarEdgesRetainPointerOwnership() {
        assertTrue(
            isPointerOverEditorScrollbar(
                pointerX = 295f,
                pointerY = 100f,
                containerWidth = 300f,
                containerHeight = 200f,
                scrollbarHitZoneWidth = 12f,
                hasHorizontalScrollbar = false
            )
        )
        assertTrue(
            isPointerOverEditorScrollbar(
                pointerX = 100f,
                pointerY = 195f,
                containerWidth = 300f,
                containerHeight = 200f,
                scrollbarHitZoneWidth = 12f,
                hasHorizontalScrollbar = true
            )
        )
    }
}
