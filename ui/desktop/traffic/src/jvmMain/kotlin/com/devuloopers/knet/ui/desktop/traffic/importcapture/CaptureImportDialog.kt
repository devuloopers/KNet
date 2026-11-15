package com.devuloopers.knet.ui.desktop.traffic.importcapture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.application.contract.device.ProjectedStateFlow
import com.devuloopers.knet.application.contract.importcapture.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.drop.*
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltipHost
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Dialog interactions are separate from file-owned immutable presentation values. */
data class CaptureImportDialogActions(
    /** Opens the native file picker while preserving any validated candidate. */
    val chooseFile: () -> Unit,
    /** Cancels the chooser or current pre-release drag preview. */
    val dismiss: () -> Unit,
    /** Loads one file-local record by index. */
    val select: (Long) -> Unit,
    /** Loads a bounded page at a file-local offset. */
    val page: (Long) -> Unit,
    /** Prepares the explicitly selected action for review. */
    val perform: (CaptureImportAction) -> Unit,
)

private data class DropAvailability(val loading: Boolean, val suppressed: Boolean)

/**
 * One app-wide native receiver. Parsing progress is observed only inside the overlay so the workspace
 * remains mounted independently. A synchronous reservation bridges native onDrop, onEnded and queued paths.
 */
@Composable
fun CaptureImportDropHost(
    viewModel: CaptureImportDialogViewModel,
    workspace: PolicyWorkspace,
    workspaceName: String,
    onReady: (CaptureImportActionDraft) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val availability by remember(viewModel) { ProjectedStateFlow(viewModel.state) {
        DropAvailability(it.loading, it.dragSuppressed)
    } }.collectAsState()
    KNetFileDropTarget(enabled = !availability.loading && !availability.suppressed,
        onDraggingChanged = viewModel::dragging,
        onDropStarted = {
            viewModel.beginDrop(workspace, workspaceName)?.let { token ->
                KNetFileDropReceiver({ viewModel.completeDrop(token, it) }, { viewModel.rejectDrop(token, it) })
            }
        }, modifier = modifier) {
        content()
        CaptureImportOverlay(viewModel, onReady)
    }
}

/** One surface remains mounted as its state changes; no native dialog or alternate screen is created. */
@Composable
private fun CaptureImportOverlay(viewModel: CaptureImportDialogViewModel, onReady: (CaptureImportActionDraft) -> Unit) {
    val state by viewModel.state.collectAsState()
    if (!state.presented) return
    val reasons = remember(state.capture, state.selected, state.loading) { CaptureImportAction.entries.associateWith(viewModel::unavailableReason) }
    KNetFileImportOverlay(viewModel::dismiss) {
        CaptureImportDialogContent(state, reasons, CaptureImportDialogActions(viewModel::chooseFile, viewModel::dismiss,
            viewModel::select, viewModel::page, { viewModel.perform(it, onReady) }))
    }
}

/** Live contents of the persistent in-window file-import surface. */
@Composable
fun CaptureImportDialogContent(
    state: CaptureImportDialogState,
    unavailable: Map<CaptureImportAction, String?>,
    actions: CaptureImportDialogActions,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetScrollableDialogBody(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(KNetIcons.FolderOpen, null, Modifier.size(KNetTheme.dimensions.iconSizeLarge), tint = colors.accent)
            Text("Drop files into KNet", Modifier.weight(1f), style = KNetTheme.typography.titleMedium,
                color = colors.textPrimary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            KNetButton(actions.dismiss, variant = ButtonVariant.Secondary, size = ButtonSize.Compact) {
                Icon(KNetIcons.Close, "Dismiss import", Modifier.size(KNetTheme.dimensions.iconSizeSmall))
            }
        }
        Text(when {
            state.dragging -> "Release to validate the file and choose an action."
            state.capture != null -> "Choose how to use this validated capture."
            else -> "Choose a file, or drop one anywhere in KNet."
        }, style = KNetTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        KNetFileDropZone(state.dragging) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(KNetIcons.FolderOpen, null, Modifier.size(KNetTheme.dimensions.iconSizeLarge), tint = colors.accent)
                Text(if (state.loading && state.preparingAction == null) "Validating capture" else state.capture?.name ?: "Drop a capture file here",
                    color = colors.textPrimary, style = KNetTheme.typography.bodyMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (state.loading) Text(state.preparingAction?.let { "Preparing editable draft" }
                    ?: state.progress?.let { "${it.records} records · ${it.payloadBytes} payload bytes" } ?: "Reading file",
                    color = colors.textSecondary, style = KNetTheme.typography.caption, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                else if (state.capture != null) Text("${state.capture.format} · ${state.capture.records} records",
                    color = colors.textSecondary, style = KNetTheme.typography.caption, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (!state.dragging) KNetButton(actions.chooseFile, enabled = !state.loading, variant = ButtonVariant.Secondary, size = ButtonSize.Compact) {
                    Text(if (state.capture == null) "Choose file" else "Choose another file", maxLines = 1, softWrap = false)
                }
            }
        }
        Text("HAR · KNet Capture · Charles XML", Modifier.align(Alignment.CenterHorizontally),
            color = colors.textMuted, style = KNetTheme.typography.caption, maxLines = 1, softWrap = false)
        state.error?.let { error -> KNetTooltipHost(error) {
            Text(error, color = colors.semantic.error, style = KNetTheme.typography.bodySmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        } }
        if (state.capture != null && state.rows.isNotEmpty()) {
            val selected = state.rows.firstOrNull { it.index == state.selected?.index } ?: state.rows.first()
            Text("Record for API Studio or rule creation", color = colors.textSecondary, style = KNetTheme.typography.caption, maxLines = 1, softWrap = false)
            KNetDropdown(selectedItem = selected, items = state.rows, enabled = !state.loading, modifier = Modifier.fillMaxWidth(),
                onItemSelected = { actions.select(it.index) }, itemText = { "${it.index + 1}. ${it.method ?: it.kind} ${it.destination}" })
            if (state.capture.records > state.rows.size) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                KNetButton({ actions.page((state.offset - 128).coerceAtLeast(0)) }, enabled = !state.loading && state.offset > 0,
                    variant = ButtonVariant.Secondary, size = ButtonSize.Compact) { Text("Previous", maxLines = 1, softWrap = false) }
                Text("${state.offset + 1}–${state.offset + state.rows.size} of ${state.capture.records}", Modifier.weight(1f),
                    color = colors.textMuted, style = KNetTheme.typography.caption, maxLines = 1, softWrap = false)
                KNetButton({ actions.page(state.offset + 128) }, enabled = !state.loading && state.offset + state.rows.size < state.capture.records,
                    variant = ButtonVariant.Secondary, size = ButtonSize.Compact) { Text("Next", maxLines = 1, softWrap = false) }
            }
        }
        Box(Modifier.fillMaxWidth().height(KNetTheme.dimensions.dividerThickness).background(colors.border))
        Text(if (state.capture == null) "Available after validation" else "Choose an action", color = colors.textSecondary,
            style = KNetTheme.typography.caption, maxLines = 1, softWrap = false)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            CaptureImportAction.entries.forEach { action ->
                val description = when (action) {
                    CaptureImportAction.API_STUDIO -> "Create an unsent request draft"
                    CaptureImportAction.INTERCEPT -> "Choose a request to pause"
                    CaptureImportAction.NETWORK_CONDITIONS -> "Choose a destination to shape"
                    CaptureImportAction.PROTECTED_TRAFFIC -> "Review destination handling"
                }
                val reason = unavailable[action]
                KNetTooltipHost(reason ?: description) {
                    Row(Modifier.fillMaxWidth().background(colors.surface, KNetTheme.shapes.medium)
                        .clickable(enabled = reason == null && state.capture != null && !state.loading) { actions.perform(action) }
                        .padding(spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        Icon(when (action) {
                            CaptureImportAction.API_STUDIO -> KNetIcons.Send
                            CaptureImportAction.INTERCEPT -> KNetIcons.Pause
                            CaptureImportAction.NETWORK_CONDITIONS -> KNetIcons.Speed
                            CaptureImportAction.PROTECTED_TRAFFIC -> KNetIcons.Shield
                        }, null, Modifier.size(KNetTheme.dimensions.iconSizeLarge), tint = colors.accent)
                        Column(Modifier.weight(1f)) {
                            Text(when (action) {
                                CaptureImportAction.API_STUDIO -> "Send to API Studio"
                                CaptureImportAction.INTERCEPT -> "Create Intercept rule"
                                CaptureImportAction.NETWORK_CONDITIONS -> "Create Network Conditions rule"
                                CaptureImportAction.PROTECTED_TRAFFIC -> "Create Protected Traffic rule"
                            }, style = KNetTheme.typography.bodySmall, color = if (reason == null || state.capture == null) colors.textPrimary else colors.textMuted,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Text(if (state.capture != null && reason != null && !state.loading) reason else description,
                                style = KNetTheme.typography.caption, color = colors.textSecondary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (state.capture != null) Text("Rule drafts for: ${state.workspaceName}", color = colors.textMuted,
            style = KNetTheme.typography.caption, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
