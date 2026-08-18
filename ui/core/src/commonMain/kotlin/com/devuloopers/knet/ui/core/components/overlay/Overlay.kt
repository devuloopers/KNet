package com.devuloopers.knet.ui.core.components.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun OverlayHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}

@Composable
fun FloatingPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors
    val shapes = KNetTheme.shapes

    KNetSurface(
        modifier = modifier,
        color = themeColors.surface,
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
fun Popover(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FloatingPanel(modifier = modifier, content = content)
}

@Composable
fun ContextOverlay(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FloatingPanel(modifier = modifier, content = content)
}
