package com.devuloopers.knet.ui.core.components.surface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.devuloopers.knet.ui.core.foundation.extensions.thenIf
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Lightweight surface container composable.
 */
@Composable
fun KNetSurface(
    modifier: Modifier = Modifier,
    color: Color = KNetTheme.colors.surface,
    contentColor: Color = KNetTheme.colors.textPrimary,
    shape: Shape = KNetTheme.shapes.none,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(color)
                .thenIf(border != null) { border(border!!) },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
