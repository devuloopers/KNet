package com.devuloopers.knet.ui.core.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Extremely thin card container surface.
 */
@Composable
fun KNetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors
    val shapes = KNetTheme.shapes

    KNetSurface(
        modifier = modifier,
        color = themeColors.surface,
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.small
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}
