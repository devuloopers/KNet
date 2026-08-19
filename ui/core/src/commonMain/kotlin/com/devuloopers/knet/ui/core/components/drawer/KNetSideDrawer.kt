package com.devuloopers.knet.ui.core.components.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.devuloopers.knet.ui.core.foundation.dimensions.Dimensions
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Width classes supported by the shared desktop side drawer. */
enum class KNetSideDrawerSize {
    /** Standard width for focused setup and detail workflows. */
    STANDARD,

    /** Expanded width for dense master-detail editors. */
    EXPANDED,
}

/**
 * Renders a reusable non-modal drawer anchored to the right edge of its bounded parent.
 *
 * The shell owns responsive width resolution, slide animation, surface color, and border only.
 * Feature modules retain ownership of visibility state, headers, scrolling, actions, and content.
 * The drawer never installs a scrim, so callers may keep the underlying desktop workspace visible.
 *
 * @param visible Whether the drawer content is visible.
 * @param size Semantic drawer width class resolved through design-system dimensions.
 * @param modifier Modifier for the full-parent overlay host.
 * @param content Feature-owned drawer content.
 */
@Composable
fun KNetSideDrawer(
    visible: Boolean,
    size: KNetSideDrawerSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    val motion = KNetTheme.motion
    val animationDuration = if (motion.animationsEnabled) motion.durationSlow else motion.durationInstant
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val drawerWidth = resolveSideDrawerWidth(size, dimensions, maxWidth)
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(
                    durationMillis = animationDuration,
                    easing = motion.easingStandard,
                ),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(
                    durationMillis = animationDuration,
                    easing = motion.easingStandard,
                ),
            ),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .background(colors.surface)
                    .border(dimensions.borderWidth, colors.border),
                content = content,
            )
        }
    }
}

/** Resolves the requested drawer width without exceeding its current parent. */
internal fun resolveSideDrawerWidth(
    size: KNetSideDrawerSize,
    dimensions: Dimensions,
    availableWidth: Dp,
): Dp {
    val preferredWidth = when (size) {
        KNetSideDrawerSize.STANDARD -> dimensions.sideDrawerWidth
        KNetSideDrawerSize.EXPANDED -> dimensions.expandedSideDrawerWidth
    }
    return minOf(preferredWidth, availableWidth)
}
