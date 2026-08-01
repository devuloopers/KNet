package com.devuloopers.knet.ui.desktop.inspector.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.feedback.EmptyState

/**
 * Image response payload viewer composable.
 */
@Composable
public fun ImageViewer(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            title = "Image Preview",
            description = "Binary image content preview."
        )
    }
}
