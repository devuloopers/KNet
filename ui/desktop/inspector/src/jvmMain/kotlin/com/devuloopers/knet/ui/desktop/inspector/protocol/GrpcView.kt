package com.devuloopers.knet.ui.desktop.inspector.protocol

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * gRPC messages inspector view composable.
 */
@Composable
public fun GrpcView(
    modifier: Modifier = Modifier
) {
    EmptyState(title = "gRPC Protobuf Streams", description = "Decoded Protobuf messages.", modifier = modifier)
}
