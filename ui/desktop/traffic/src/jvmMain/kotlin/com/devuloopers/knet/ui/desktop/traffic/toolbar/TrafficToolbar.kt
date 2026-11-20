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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownSize
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode
import com.devuloopers.knet.ui.core.components.toolbar.KNetToolbar
import com.devuloopers.knet.ui.core.components.toolbar.ToolbarGroup
import com.devuloopers.knet.ui.core.foundation.animation.InlineRevealAnimation
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.CaptureState

/**
 * Immutable State DTO for TrafficToolbar.
 */
data class TrafficToolbarState(
    val captureState: CaptureState = CaptureState.STOPPED,
    val engineState: ProxyRuntimeState = ProxyRuntimeState.Stopped,
    val scrollSettings: TrafficAutoScrollSettings = TrafficAutoScrollSettings(),
    val localIpAddress: String = "127.0.0.1",
    val isClearingHistory: Boolean = false,
    val exportAvailable: Boolean = false,
    /** Explicit selection count; zero uses the ordinary source/filter export dialog. */
    val selectedCount: Int = 0,
    /** Source whose recording is controlled; the proxy listener itself remains shared. */
    val source: com.devuloopers.knet.application.contract.traffic.TrafficSource =
        com.devuloopers.knet.application.contract.traffic.TrafficSource.Other,
)

/**
 * Action Callbacks DTO for TrafficToolbar.
 */
data class TrafficToolbarActions(
    val onStartCapture: () -> Unit = {},
    val onPauseCapture: () -> Unit = {},
    val onClearFeed: () -> Unit = {},
    val onExport: (() -> Unit)? = null,
    val onImport: (() -> Unit)? = null,
    val onAutoScrollToggle: () -> Unit = {},
    val onAutoScrollModeSelected: (TrafficAutoScrollMode) -> Unit = {},
)

/**
 * Traffic controls use the shared toolbar's control row and separate horizontal-overflow scrollbar.
 */
@Composable
fun TrafficToolbar(
    state: TrafficToolbarState,
    actions: TrafficToolbarActions,
    modifier: Modifier = Modifier,
    networkConditionsControl: @Composable () -> Unit = {},
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing
    val dimensions = KNetTheme.dimensions

    val isCapturing = state.captureState == CaptureState.CAPTURING
    val captureTransitioning = state.captureState == CaptureState.STARTING
    val canStartCapture = !isCapturing && !captureTransitioning
    val captureScope = if (state.source is com.devuloopers.knet.application.contract.traffic.TrafficSource.Device)
        "the selected device" else "Other traffic"

    KNetToolbar(
        scrollLeading = true,
        modifier = modifier
            .border(width = 1.dp, color = themeColors.border)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        leading = {
            ToolbarGroup(
                modifier = Modifier.padding(end = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // Start Button
                Row(
                    modifier = Modifier
                        .clip(shapes.small)
                        .background(if (canStartCapture) themeColors.accent else themeColors.border)
                        .clickable(enabled = canStartCapture) { actions.onStartCapture() }
                        .semantics { contentDescription = "Start Capture for $captureScope" }
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
                        .semantics { contentDescription = "Pause Capture for $captureScope" }
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
                val engine = state.engineState
                // Running endpoints use their full width in the shared horizontal scroll area.
                val statusWidth = if (engine is ProxyRuntimeState.Running) Modifier
                    else Modifier.widthIn(max = dimensions.toolbarStatusMaxWidth)
                Row(
                    modifier = statusWidth
                        .clip(shapes.small)
                        .background(themeColors.surfaceVariant)
                        .border(1.dp, themeColors.border, shapes.small)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    val (dotColor, statusText) = when (engine) {
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

                actions.onImport?.let { open ->
                    com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportButton(enabled = true, onClick = open,
                        label = "Import", description = "Open HAR, Charles XML or KNet Capture as separate historical traffic.")
                }
                actions.onExport?.let { export ->
                    com.devuloopers.knet.ui.desktop.httppanel.export.CaptureExportButton(enabled = state.exportAvailable && !state.isClearingHistory, onClick = export,
                        label = if (state.selectedCount > 0) "Export (${state.selectedCount})" else "Export",
                        description = if (state.selectedCount > 0) "Export exactly ${state.selectedCount} selected items. Clear selection to export filtered results."
                        else "Export retained traffic for this source or its current filters. Command/Ctrl-click adds rows; Shift-click selects a range.")
                }
            }
        },
        trailing = {
            BoxWithConstraints {
                // The selected label can grow while capture actions and Auto Scroll retain their space.
                val maximumConditionsWidth = maxWidth * 0.3f
                ToolbarGroup(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Box(Modifier.widthIn(max = maximumConditionsWidth)) {
                        networkConditionsControl()
                    }
                    TrafficAutoScrollControl(state.scrollSettings, actions.onAutoScrollModeSelected, actions.onAutoScrollToggle)
                }
            }
        },
        content = {},
    )
}

/** Shared-theme Auto scroll selector; the Manual strategy exposes its explicit switch beside the mode. */
@Composable
internal fun TrafficAutoScrollControl(
    settings: TrafficAutoScrollSettings,
    onModeSelected: (TrafficAutoScrollMode) -> Unit,
    onManualToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Auto scroll", modifier = Modifier.padding(end = KNetTheme.spacing.sm), style = KNetTheme.typography.bodySmall, color = KNetTheme.colors.textSecondary,
            maxLines = 1, softWrap = false)
        KNetDropdown(selectedItem = settings.mode, items = TrafficAutoScrollMode.entries,
            onItemSelected = onModeSelected, size = KNetDropdownSize.Compact, animateSelection = true,
            itemText = { mode -> when (mode) { TrafficAutoScrollMode.SMART -> "Smart"; TrafficAutoScrollMode.MANUAL -> "Manual" } },
            itemDescription = { mode -> when (mode) {
                TrafficAutoScrollMode.SMART -> "Follows new traffic at the top. Scrolling into history pauses it; returning to the top resumes it."
                TrafficAutoScrollMode.MANUAL -> "Use the switch to turn auto scroll on or off. Jumping to new traffic leaves the switch unchanged."
            } })
        InlineRevealAnimation(visible = settings.mode == TrafficAutoScrollMode.MANUAL) {
            Box(Modifier.padding(start = KNetTheme.spacing.sm)) {
                KNetSwitch(
                    checked = settings.manualEnabled,
                    onCheckedChange = { if (settings.mode == TrafficAutoScrollMode.MANUAL) onManualToggle() },
                    enabled = settings.mode == TrafficAutoScrollMode.MANUAL,
                    modifier = Modifier.semantics { contentDescription = "Manual auto scroll" },
                    label = if (settings.manualEnabled) "On" else "Off",
                )
            }
        }
    }
}
