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
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Lightweight shimmer placeholder rendered while arbitrary content is processed off-thread.
 *
 * Provides smooth, non-blocking visual feedback when switching between large traffic inspect items.
 */
@Composable
fun KNetContentLoadingPlaceholder(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetTheme.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val lineFractions = listOf(0.75f, 0.90f, 0.45f, 0.80f, 0.60f, 0.85f, 0.50f, 0.70f)
        lineFractions.forEach { fraction ->
            KNetShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(16.dp)
            )
        }
    }
}

/** Theme-aware fixed-size shimmer surface for layouts that reserve space while data is loading. */
@Composable
fun KNetShimmerBox(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(rememberKNetShimmerBrush()),
    )
}

@Composable
private fun rememberKNetShimmerBrush(): Brush {
    val colors = KNetTheme.colors
    val motion = KNetTheme.motion
    val alpha = if (motion.animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "KNetShimmer")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.durationSlow * 3, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "KNetShimmerAlpha",
        )
        animatedAlpha
    } else {
        0.35f
    }
    return Brush.linearGradient(
        colors = listOf(
            colors.surfaceVariant,
            colors.border.copy(alpha = alpha),
            colors.surfaceVariant,
        ),
    )
}
