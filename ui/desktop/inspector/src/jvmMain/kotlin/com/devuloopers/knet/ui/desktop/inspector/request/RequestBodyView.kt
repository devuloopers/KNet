package com.devuloopers.knet.ui.desktop.inspector.request

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.desktop.inspector.viewer.RawViewer

/**
 * Request body viewer composable.
 */
@Composable
public fun RequestBodyView(
    body: String,
    modifier: Modifier = Modifier
) {
    RawViewer(rawContent = body, modifier = modifier)
}
