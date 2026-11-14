package com.devuloopers.knet.ui.core.foundation.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * High-density IDE expansion/collapse animation wrapper composable.
 */
@Composable
fun ExpandCollapseAnimation(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        content()
    }
}

/**
 * Reveals an optional inline control without changing the containing toolbar's height.
 * Width and opacity follow KNet motion tokens, including immediate reduced-motion transitions.
 * Content-size changes inside the slot (for example an On/Off label) use the same timing.
 * @param visible Whether the inline content should be present after the transition.
 * @param modifier Placement of the animated slot; keep its spacing inside [content] so it animates too.
 * @param content Retained until the exit transition completes, preserving control-local state.
 */
@Composable
fun InlineRevealAnimation(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandHorizontally(tween(duration, easing = motion.easingStandard), expandFrom = Alignment.End) +
            fadeIn(tween(duration, easing = motion.easingStandard)),
        exit = shrinkHorizontally(tween(duration, easing = motion.easingStandard), shrinkTowards = Alignment.End) +
            fadeOut(tween(duration, easing = motion.easingStandard)),
    ) {
        Box(Modifier.animateContentSize(tween(duration, easing = motion.easingStandard), alignment = Alignment.CenterEnd)) {
            content()
        }
    }
}
