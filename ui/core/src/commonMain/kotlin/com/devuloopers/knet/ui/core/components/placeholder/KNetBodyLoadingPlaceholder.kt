package com.devuloopers.knet.ui.core.components.placeholder

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Lightweight shimmer placeholder rendered while HTTP response body payloads are processed off-thread.
 *
 * Provides smooth, non-blocking visual feedback when switching between large traffic inspect items.
 */
@Composable
public fun KNetBodyLoadingPlaceholder(
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors

    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val baseColor = themeColors.surfaceVariant
    val shimmerColor = themeColors.border.copy(alpha = alpha)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, shimmerColor, baseColor)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val lineFractions = listOf(0.75f, 0.90f, 0.45f, 0.80f, 0.60f, 0.85f, 0.50f, 0.70f)
        lineFractions.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
        }
    }
}
