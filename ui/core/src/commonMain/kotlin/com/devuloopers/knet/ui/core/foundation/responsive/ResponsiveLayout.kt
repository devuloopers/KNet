package com.devuloopers.knet.ui.core.foundation.responsive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Responsive layout container computing window dimensions and passing [WindowInfo] down to children.
 */
@Composable
fun ResponsiveLayout(
    modifier: Modifier = Modifier,
    content: @Composable (windowInfo: WindowInfo) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val windowInfo = calculateWindowInfo(maxWidth, maxHeight)
        content(windowInfo)
    }
}
