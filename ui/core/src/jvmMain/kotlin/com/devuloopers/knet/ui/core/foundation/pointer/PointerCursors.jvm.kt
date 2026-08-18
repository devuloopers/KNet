package com.devuloopers.knet.ui.core.foundation.pointer

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

/** Desktop horizontal-resize cursor backed by the AWT cursor used by Compose Desktop. */
actual fun Modifier.resizeHorizontalCursor(): Modifier = pointerHoverIcon(
    PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)),
)

/** Desktop vertical-resize cursor backed by the AWT cursor used by Compose Desktop. */
actual fun Modifier.resizeVerticalCursor(): Modifier = pointerHoverIcon(
    PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)),
)
