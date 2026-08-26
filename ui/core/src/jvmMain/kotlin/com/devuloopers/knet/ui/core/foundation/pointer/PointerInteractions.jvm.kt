package com.devuloopers.knet.ui.core.foundation.pointer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/** Applies desktop hover callbacks to this modifier. */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.platformHoverEvents(
    onEnter: () -> Unit,
    onMove: () -> Unit,
    onExit: () -> Unit,
): Modifier = this
    .onPointerEvent(PointerEventType.Enter) { onEnter() }
    .onPointerEvent(PointerEventType.Move) { onMove() }
    .onPointerEvent(PointerEventType.Exit) { onExit() }

/** Opens a desktop context menu from a secondary-button press. */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.platformContextMenuGesture(
    gestureKey: Any?,
    onOpen: (IntOffset) -> Unit,
): Modifier = pointerInput(gestureKey) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.button == PointerButton.Secondary) {
                event.changes.firstOrNull()?.let { change ->
                    onOpen(IntOffset(change.position.x.toInt(), change.position.y.toInt()))
                    change.consume()
                }
            }
        }
    }
}
