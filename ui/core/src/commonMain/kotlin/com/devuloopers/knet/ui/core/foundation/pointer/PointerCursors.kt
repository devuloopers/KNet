package com.devuloopers.knet.ui.core.foundation.pointer

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

/**
 * Modifier extension adding custom pointer cursor hovering effect.
 */
fun Modifier.pointerCursor(cursorType: Int): Modifier {
    return this.pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(cursorType)))
}

fun Modifier.handCursor(): Modifier = pointerCursor(Cursor.HAND_CURSOR)
fun Modifier.resizeHorizontalCursor(): Modifier = pointerCursor(Cursor.E_RESIZE_CURSOR)
fun Modifier.resizeVerticalCursor(): Modifier = pointerCursor(Cursor.N_RESIZE_CURSOR)
fun Modifier.textCursor(): Modifier = pointerCursor(Cursor.TEXT_CURSOR)
