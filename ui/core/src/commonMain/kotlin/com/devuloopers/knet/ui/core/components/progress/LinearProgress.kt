package com.devuloopers.knet.ui.core.components.progress

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun LinearProgress(
    modifier: Modifier = Modifier,
    color: Color = KNetTheme.colors.accent,
    trackColor: Color = KNetTheme.colors.surfaceVariant
) {
    LinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor
    )
}

@Composable
public fun CircularProgress(
    modifier: Modifier = Modifier,
    color: Color = KNetTheme.colors.accent
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color
    )
}
