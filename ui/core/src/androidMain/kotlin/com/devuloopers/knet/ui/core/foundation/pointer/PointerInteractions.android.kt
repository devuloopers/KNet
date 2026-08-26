package com.devuloopers.knet.ui.core.foundation.pointer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/** Leaves hover behavior inactive for Android touch input. */
internal actual fun Modifier.platformHoverEvents(
    onEnter: () -> Unit,
    onMove: () -> Unit,
    onExit: () -> Unit,
): Modifier = this

/** Opens an Android context menu from a long press. */
internal actual fun Modifier.platformContextMenuGesture(
    gestureKey: Any?,
    onOpen: (IntOffset) -> Unit,
): Modifier = pointerInput(gestureKey) {
    detectTapGestures(
        onLongPress = { position -> onOpen(IntOffset(position.x.toInt(), position.y.toInt())) },
    )
}
