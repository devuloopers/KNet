package com.devuloopers.knet.ui.core.foundation.pointer

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/** Uses Compose's portable hand cursor for clickable content. */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/** Uses Compose's portable text cursor for editable content. */
fun Modifier.textCursor(): Modifier = pointerHoverIcon(PointerIcon.Text)

/** Uses the platform's horizontal-resize cursor. */
expect fun Modifier.resizeHorizontalCursor(): Modifier

/** Uses the platform's vertical-resize cursor. */
expect fun Modifier.resizeVerticalCursor(): Modifier
