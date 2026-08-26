package com.devuloopers.knet.ui.desktop.traffic.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.components.toolbar.KNetToolbar
import com.devuloopers.knet.ui.core.components.toolbar.ToolbarGroup
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.CaptureState

/**
 * Immutable State DTO for TrafficToolbar.
 */
data class TrafficToolbarState(
    val captureState: CaptureState = CaptureState.STOPPED,
    val engineState: ProxyRuntimeState = ProxyRuntimeState.Stopped,
    val autoScroll: Boolean = true,
    val localIpAddress: String = "127.0.0.1",
    val isClearingHistory: Boolean = false,
)

/**
 * Action Callbacks DTO for TrafficToolbar.
 */
data class TrafficToolbarActions(
    val onStartCapture: () -> Unit = {},
    val onPauseCapture: () -> Unit = {},
    val onClearFeed: () -> Unit = {},
    val onAutoScrollToggle: () -> Unit = {}
)

/**
 * 56dp Top Feature Toolbar bound strictly to :ui:core design system tokens and parameter objects.
 */
@Composable
fun TrafficToolbar(
    state: TrafficToolbarState,
    actions: TrafficToolbarActions,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing
    val dimensions = KNetTheme.dimensions

    val isCapturing = state.captureState == CaptureState.CAPTURING
    val captureTransitioning = state.captureState == CaptureState.STARTING
    val canStartCapture = !isCapturing && !captureTransitioning

    KNetToolbar(
        modifier = modifier
            .border(width = 1.dp, color = themeColors.border)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        leading = {
            ToolbarGroup(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                // Start Button
                Row(
                    modifier = Modifier
                        .clip(shapes.small)
                        .background(if (canStartCapture) themeColors.accent else themeColors.border)
                        .clickable(enabled = canStartCapture) { actions.onStartCapture() }
                        .padding(horizontal = spacing.md, vertical = spacing.xs)
                        .handCursor(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Capture",
                        tint = if (canStartCapture) themeColors.textPrimary else themeColors.textMuted,
                        modifier = Modifier.size(dimensions.iconSizeSmall)
                    )
                    Text(
                        text = "Start Capture",
                        style = typography.titleSmall.copy(
                            color = if (canStartCapture) themeColors.textPrimary else themeColors.textMuted,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Pause Capture Button
                Row(
                    modifier = Modifier
                        .clip(shapes.small)
                        .background(if (isCapturing) themeColors.semantic.errorContainer else themeColors.border)
                        .clickable(enabled = isCapturing) { actions.onPauseCapture() }
                        .padding(horizontal = spacing.md, vertical = spacing.xs)
                        .handCursor(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isCapturing) themeColors.semantic.error else themeColors.textMuted,
                                shape = shapes.small
                            )
                    )
                    Text(
                        text = "Pause Capture",
                        style = typography.titleSmall.copy(
                            color = if (isCapturing) themeColors.semantic.error else themeColors.textMuted,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Engine Operational Status Badge
                Row(
                    modifier = Modifier
                        .clip(shapes.small)
                        .background(themeColors.surfaceVariant)
                        .border(1.dp, themeColors.border, shapes.small)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    val (dotColor, statusText) = when (val engine = state.engineState) {
                        is ProxyRuntimeState.Running -> {
                            val port = engine.handle.endpoints.endpoints.firstOrNull()?.port ?: "?"
                            val activity = if (isCapturing) "Capturing" else "Forwarding · Capture paused"
                            themeColors.semantic.success to "$activity (${state.localIpAddress}:$port)"
                        }
                        ProxyRuntimeState.Starting -> themeColors.accent to "Starting..."
                        ProxyRuntimeState.Stopping -> themeColors.textMuted to "Stopping..."
                        ProxyRuntimeState.Stopped -> themeColors.textMuted to "Stopped"
                        is ProxyRuntimeState.Failed -> themeColors.semantic.error to "Error: ${engine.code}"
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(dotColor, shape = shapes.pill)
                    )
                    Text(
                        text = statusText,
                        style = typography.labelMedium.copy(
                            color = themeColors.textSecondary,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Clear Button
                Row(
                    modifier = Modifier
                        .clip(shapes.small)
                        .background(themeColors.border)
                        .clickable(enabled = !state.isClearingHistory) { actions.onClearFeed() }
                        .padding(horizontal = spacing.sm, vertical = spacing.xs)
                        .handCursor(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        tint = themeColors.textSecondary,
                        modifier = Modifier.size(dimensions.iconSizeSmall)
                    )
                    Text(
                        text = if (state.isClearingHistory) "Clearing…" else "Clear",
                        style = typography.bodySmall.copy(
                            color = themeColors.textSecondary
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        trailing = {
            ToolbarGroup {
                KNetSwitch(
                    checked = state.autoScroll,
                    onCheckedChange = { actions.onAutoScrollToggle() },
                    label = "Auto Scroll"
                )
            }
        }
    )
}
