package com.devuloopers.knet.ui.core.components.table

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.resizeHorizontalCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Header-edge interaction target for resizing a caller-owned table column.
 *
 * A short centered tick keeps column boundaries discoverable without drawing a full-height header grid. The tick
 * expands and becomes accent-colored during hover or drag while its full-height hit target remains wide enough
 * for accurate desktop interaction. Deltas are reported in density-independent pixels. A double click requests
 * reset; persistence remains the feature owner's responsibility through [onResizeFinished].
 *
 * @param widthDp Current rendered width used as the drag gesture's starting value.
 * @param onWidthChange Invoked with the absolute proposed width throughout the gesture.
 * @param onResizeFinished Invoked once after a completed or cancelled resize gesture.
 * @param onReset Invoked when the user double-clicks the separator.
 * @param idleIndicatorVisible Whether the neutral indicator is visible before hover or drag. Disable this for a
 * final table boundary that must remain resizable without appearing as an interior separator.
 * @param modifier Modifier applied to the resize hit target.
 */
@Composable
fun KNetColumnResizeHandle(
    widthDp: Float,
    onWidthChange: (Float) -> Unit,
    onResizeFinished: () -> Unit,
    onReset: () -> Unit,
    idleIndicatorVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val motion = KNetTheme.motion
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val currentWidthDp by rememberUpdatedState(widthDp)
    val currentWidthChange by rememberUpdatedState(onWidthChange)
    val currentResizeFinished by rememberUpdatedState(onResizeFinished)
    val currentReset by rememberUpdatedState(onReset)
    val isActive = hovered || dragging
    val animationDuration = if (motion.animationsEnabled) motion.durationFast else motion.durationInstant
    val indicatorWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        animationSpec = tween(animationDuration, easing = motion.easingStandard),
        label = "columnResizeIndicatorWidth",
    )
    val indicatorHeight by animateDpAsState(
        targetValue = if (isActive) 22.dp else 14.dp,
        animationSpec = tween(animationDuration, easing = motion.easingStandard),
        label = "columnResizeIndicatorHeight",
    )
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isActive -> colors.accent
            idleIndicatorVisible -> colors.textMuted.copy(alpha = 0.55f)
            else -> colors.textMuted.copy(alpha = 0f)
        },
        animationSpec = tween(animationDuration, easing = motion.easingStandard),
        label = "columnResizeIndicatorColor",
    )

    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .resizeHorizontalCursor()
            .pointerInput(Unit) {
                var dragStartWidthDp = currentWidthDp
                var accumulatedDragDp = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragStartWidthDp = currentWidthDp
                        accumulatedDragDp = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        currentResizeFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        currentResizeFinished()
                    },
                    onHorizontalDrag = { change, dragAmountPx ->
                        change.consume()
                        accumulatedDragDp += with(density) { dragAmountPx.toDp().value }
                        currentWidthChange(dragStartWidthDp + accumulatedDragDp)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { currentReset() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(indicatorHeight)
                .background(indicatorColor, CircleShape),
        )
    }
}
