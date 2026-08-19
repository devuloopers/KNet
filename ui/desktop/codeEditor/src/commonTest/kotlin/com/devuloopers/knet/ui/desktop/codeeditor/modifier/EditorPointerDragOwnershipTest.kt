package com.devuloopers.knet.ui.desktop.codeeditor.modifier

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
