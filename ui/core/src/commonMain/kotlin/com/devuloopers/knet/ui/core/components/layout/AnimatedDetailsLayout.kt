package com.devuloopers.knet.ui.core.components.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Reserves space for a right inspector while revealing its stable-width content.
 *
 * The initial visible state is laid out immediately (including return from a tab). Subsequent
 * visibility changes use shared motion. Callers retain the previous detail content during exit.
 * Responsive limits belong here; content is never laid out at an intermediate narrow width.
 */
@Composable
fun KNetAnimatedDetailsLayout(
    detailsVisible: Boolean,
    mainContent: @Composable (Modifier) -> Unit,
    detailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = KNetTheme.dimensions
    val motion = KNetTheme.motion
    BoxWithConstraints(modifier.fillMaxSize()) {
        val targetWidth = (maxWidth * 0.38f).coerceIn(dimensions.inspectorMinWidth, dimensions.inspectorMaxWidth)
            .coerceAtMost((maxWidth - dimensions.inspectorMinWidth).coerceAtLeast(0.dp))
        val width by animateDpAsState(
            if (detailsVisible) targetWidth else 0.dp,
            if (motion.animationsEnabled) tween(motion.durationNormal, easing = motion.easingStandard) else snap(),
            label = "Inspector width",
        )
        val opacity by animateFloatAsState(
            if (detailsVisible) 1f else 0f,
            if (motion.animationsEnabled) tween(motion.durationNormal, easing = motion.easingStandard) else snap(),
            label = "Inspector opacity",
        )
        Row(Modifier.fillMaxSize()) {
            mainContent(Modifier.weight(1f).fillMaxHeight())
            if (detailsVisible || width > 0.dp) {
                Box(Modifier.width(width).fillMaxHeight().clipToBounds().alpha(opacity)) {
                    Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth(targetWidth).fillMaxHeight()) { detailContent() }
                }
            }
        }
    }
}
