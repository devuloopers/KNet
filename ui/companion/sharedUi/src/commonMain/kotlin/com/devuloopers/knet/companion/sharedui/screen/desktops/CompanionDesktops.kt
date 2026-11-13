package com.devuloopers.knet.companion.sharedui.screen.desktops

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.application.usecase.CompanionDesktopSwitchPhase
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.*
import com.devuloopers.knet.companion.sharedui.component.*
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Scrollable desktop manager available from both certificate setup and operational Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanionDesktopsSheet(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    val confirm = state.desktopManagement as? CompanionDesktopManagementState.ConfirmForget
    ModalBottomSheet(onDismissRequest = { if (!state.operationInProgress) onAction(CompanionAction.DesktopManagementDismissed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = KNetTheme.colors.surfaceVariant, contentColor = KNetTheme.colors.textPrimary, shape = KNetTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.desktops_title), Modifier.weight(1f), style = KNetTheme.typography.hero)
                IconButton(onClick = { onAction(CompanionAction.DesktopManagementDismissed) }, enabled = !state.operationInProgress) {
                    Icon(KNetIcons.Close, stringResource(Res.string.dismiss))
                }
            }
            Text(stringResource(Res.string.saved_desktop_count, state.registrations.size),
                style = KNetTheme.typography.bodyLarge, color = KNetTheme.colors.textSecondary)
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.registrations, key = { it.desktopId.value }) { registration ->
                    DesktopRow(registration, state.activeRegistration?.desktopId == registration.desktopId,
                        !state.operationInProgress, onAction)
                }
            }
            if (state.registrations.isEmpty()) Text(stringResource(Res.string.no_saved_desktops),
                style = KNetTheme.typography.bodyLarge, color = KNetTheme.colors.textSecondary)
            state.failure?.let { CompanionInfo(it.message, KNetIcons.Warning, KNetTheme.colors.semantic.warning) }
            CompanionActionButton(stringResource(Res.string.add_desktop), { onAction(CompanionAction.AddDesktopRequested) },
                enabled = !state.operationInProgress)
            Text(stringResource(Res.string.one_active_desktop), style = KNetTheme.typography.bodySmall, color = KNetTheme.colors.textMuted)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirm != null) AlertDialog(
        onDismissRequest = { if (!state.operationInProgress) onAction(CompanionAction.OpenDesktopsRequested) },
        containerColor = KNetTheme.colors.surfaceVariant,
        title = { Text(stringResource(Res.string.forget_desktop_title), style = KNetTheme.typography.heading) },
        text = { Text(stringResource(Res.string.forget_desktop_message, confirm.registration.desktopDisplayName.value),
            style = KNetTheme.typography.bodyLarge) },
        confirmButton = { KNetButton({ onAction(CompanionAction.ForgetDesktopRequested(confirm.registration.desktopId)) },
            variant = ButtonVariant.Danger, size = ButtonSize.Touch, enabled = !state.operationInProgress,
            loading = state.operationInProgress) { Text(stringResource(Res.string.forget_desktop)) } },
        dismissButton = { KNetButton({ onAction(CompanionAction.OpenDesktopsRequested) }, variant = ButtonVariant.Ghost,
            size = ButtonSize.Touch, enabled = !state.operationInProgress) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun DesktopRow(registration: CompanionRegistration, active: Boolean, enabled: Boolean,
    onAction: (CompanionAction) -> Unit) {
    var menu by remember(registration.desktopId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(KNetIcons.Desktop, null, Modifier.size(24.dp), tint = KNetTheme.colors.textSecondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(registration.desktopDisplayName.value, style = KNetTheme.typography.titleLarge, color = KNetTheme.colors.textPrimary)
                Text(registration.controlEndpoint.host, style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textSecondary)
            }
            KNetButton({ onAction(CompanionAction.RegistrationSelected(registration.desktopId)) },
                variant = if (active) ButtonVariant.Ghost else ButtonVariant.Secondary,
                size = ButtonSize.Touch, enabled = enabled && !active) {
                Text(stringResource(if (active) Res.string.desktop_active else Res.string.desktop_switch))
            }
            Box {
                IconButton({ menu = true }, enabled = enabled) {
                    Icon(KNetIcons.More, stringResource(Res.string.desktop_options, registration.desktopDisplayName.value))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.forget_desktop)) }, onClick = {
                        menu = false
                        onAction(CompanionAction.ConfirmForgetDesktopRequested(registration.desktopId))
                    })
                }
            }
        }
        HorizontalDivider(color = KNetTheme.colors.border)
    }
}

/** Stable handoff progress and recovery replace the manager while the native capture boundary changes. */
@Composable
internal fun CompanionDesktopSwitchScreen(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    val switching = state.desktopManagement as? CompanionDesktopManagementState.Switching
    val failed = state.desktopManagement as? CompanionDesktopManagementState.SwitchFailed
    val target = switching?.target ?: failed?.target ?: return
    CompanionHeroPage {
        if (switching != null) {
            CompanionHeading(stringResource(Res.string.desktop_switching_title), stringResource(Res.string.switching_summary), centered = true)
            CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.DESKTOP,
                stringResource(Res.string.desktop_switching_action), busy = true))
            CompanionPanel {
                CompanionStatusRow(stringResource(Res.string.switch_from), switching.source?.desktopDisplayName?.value.orEmpty())
                HorizontalDivider(color = KNetTheme.colors.border)
                CompanionStatusRow(stringResource(Res.string.switch_to), target.desktopDisplayName.value)
                HorizontalDivider(color = KNetTheme.colors.border)
                CompanionDesktopSwitchPhase.entries.forEach { phase ->
                    val label = when (phase) {
                        CompanionDesktopSwitchPhase.STOPPING_INSPECTION -> Res.string.switch_stop_step
                        CompanionDesktopSwitchPhase.VERIFYING_DESKTOP -> Res.string.switch_verify_step
                        CompanionDesktopSwitchPhase.RESUMING_INSPECTION -> Res.string.switch_resume_step
                    }
                    val done = phase.ordinal < switching.phase.ordinal
                    CompanionInfo(stringResource(label), if (done) KNetIcons.Check else KNetIcons.Schedule,
                        if (done) KNetTheme.colors.semantic.success else KNetTheme.colors.textSecondary,
                        busy = phase == switching.phase)
                }
            }
            CompanionFootnote(stringResource(Res.string.switching_note))
        } else if (failed != null) {
            CompanionHeading(stringResource(Res.string.desktop_switch_failed_title), centered = true)
            CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.RETRY,
                stringResource(Res.string.retry), stringResource(Res.string.retry_switch))) {
                onAction(CompanionAction.RegistrationSelected(target.desktopId))
            }
            CompanionPanel {
                CompanionInfo(failed.failure.message, KNetIcons.Warning, KNetTheme.colors.semantic.warning)
                HorizontalDivider(color = KNetTheme.colors.border)
                CompanionStatusRow(stringResource(Res.string.desktop_still_selected),
                    state.activeRegistration?.desktopDisplayName?.value ?: stringResource(Res.string.no_desktop_selected))
            }
            CompanionActionButton(stringResource(Res.string.back_to_desktops),
                { onAction(CompanionAction.OpenDesktopsRequested) }, variant = ButtonVariant.Ghost)
            CompanionFootnote(stringResource(Res.string.desktops_unchanged))
        }
    }
}

/** App-owned explanation precedes native VPN consent; this never impersonates a system permission dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanionVpnConsentSheet(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onAction(CompanionAction.InspectionPermissionDismissed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = KNetTheme.colors.surfaceVariant) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CompanionHeading(stringResource(Res.string.vpn_explanation_title), stringResource(Res.string.vpn_explanation_message,
                state.activeRegistration?.desktopDisplayName?.value.orEmpty()))
            CompanionPanel {
                CompanionInfo(stringResource(Res.string.vpn_local_connection), KNetIcons.Desktop)
                HorizontalDivider(color = KNetTheme.colors.border)
                CompanionInfo(stringResource(Res.string.vpn_stop_anytime), KNetIcons.Shield)
            }
            CompanionActionButton(stringResource(Res.string.certificate_continue), { onAction(CompanionAction.VpnConsentRequested) })
            CompanionActionButton(stringResource(Res.string.not_now), { onAction(CompanionAction.InspectionPermissionDismissed) }, variant = ButtonVariant.Ghost)
            Text(stringResource(Res.string.vpn_system_prompt), style = KNetTheme.typography.bodySmall, color = KNetTheme.colors.textSecondary)
        }
    }
}
