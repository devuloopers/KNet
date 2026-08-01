package com.devuloopers.knet.ui.desktop.inspector.protocol

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * WebSocket frames inspector view composable.
 */
@Composable
public fun WebSocketView(
    modifier: Modifier = Modifier
) {
    EmptyState(title = "WebSocket Frames", description = "Captured WebSocket frame log.", modifier = modifier)
}
