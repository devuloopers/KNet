package com.devuloopers.knet.ui.desktop.inspector.response

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.inspector.viewer.JsonViewer

/**
 * Response body viewer composable.
 */
@Composable
public fun ResponseBodyView(
    body: String,
    modifier: Modifier = Modifier
) {
    JsonViewer(jsonText = body, modifier = modifier)
}
