package com.devuloopers.knet.ui.desktop.inspector.protocol

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * HTTP/2 frames inspector view composable.
 */
@Composable
public fun Http2View(
    modifier: Modifier = Modifier
) {
    EmptyState(title = "HTTP/2 Frames", description = "SETTINGS, HEADERS, DATA, RST_STREAM frames.", modifier = modifier)
}
