package com.devuloopers.knet.ui.desktop.inspector.protocol

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * HTTP/3 QUIC streams inspector view composable.
 */
@Composable
public fun Http3View(
    modifier: Modifier = Modifier
) {
    EmptyState(title = "HTTP/3 QUIC Streams", description = "QUIC packet stream logs.", modifier = modifier)
}
