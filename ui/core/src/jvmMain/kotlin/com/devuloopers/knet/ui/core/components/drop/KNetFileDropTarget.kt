package com.devuloopers.knet.ui.core.components.drop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import com.devuloopers.knet.ui.core.foundation.drop.acceptsLocalFileDrop
import com.devuloopers.knet.ui.core.foundation.drop.receiveLocalFileDrop

/** Callbacks bound to one accepted native drop, captured before its asynchronous delivery. */
data class KNetFileDropReceiver(
    /** Receives bounded local paths after the native drop callback returns. */
    val files: (List<String>) -> Unit,
    /** Receives a transfer error after the native drop callback returns. */
    val rejected: (String) -> Unit,
)

/**
 * One stable desktop drop receiver; it observes entry/exit before release without replacing Compose's native target.
 * File bytes are read only by application use cases after a drop. Copy is negotiated explicitly so the source
 * application cannot delete or move the original file after successful import.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KNetFileDropTarget(
    enabled: Boolean = true,
    onDraggingChanged: (Boolean) -> Unit,
    onDropStarted: () -> KNetFileDropReceiver?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDragging by rememberUpdatedState(onDraggingChanged)
    val currentDropStarted by rememberUpdatedState(onDropStarted)
    val target = remember { object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) { if (currentEnabled) currentDragging(true) }
        override fun onEntered(event: DragAndDropEvent) { if (currentEnabled) currentDragging(true) }
        override fun onExited(event: DragAndDropEvent) { currentDragging(false) }
        override fun onEnded(event: DragAndDropEvent) { currentDragging(false) }
        override fun onDrop(event: DragAndDropEvent): Boolean {
            if (!currentEnabled) return false
            // Reserve presentation before onEnded or queued delivery can remove the preview.
            // The receiver rechecks current controller state, including a just-pressed Escape.
            val receiver = currentDropStarted() ?: return false
            currentDragging(false)
            return receiveLocalFileDrop(event, receiver.files, receiver.rejected)
        }
    } }
    Box(modifier.dragAndDropTarget(shouldStartDragAndDrop = { event ->
        currentEnabled && acceptsLocalFileDrop(event)
    }, target = target), content = content)
}
