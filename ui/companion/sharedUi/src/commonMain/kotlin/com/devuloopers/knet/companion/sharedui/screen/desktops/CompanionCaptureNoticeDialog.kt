package com.devuloopers.knet.companion.sharedui.screen.desktops

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Explains recording readiness without pretending that a working VPN is itself desktop capture. */
@Composable
internal fun CompanionCaptureNoticeDialog(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    if (state.desktopCaptureNotice == null) return
    val running = state.inspection is CompanionInspectionState.Running
    AlertDialog(
        onDismissRequest = { onAction(CompanionAction.CaptureNoticeDismissed) },
        containerColor = KNetTheme.colors.surfaceVariant,
        title = { Text(stringResource(if (running) Res.string.capture_paused_title else Res.string.capture_start_title), style = KNetTheme.typography.heading) },
        text = { Text(stringResource(if (running) Res.string.capture_paused_message else Res.string.capture_start_message,
            state.activeRegistration?.desktopDisplayName?.value.orEmpty()), style = KNetTheme.typography.bodyLarge) },
        confirmButton = { KNetButton({ onAction(if (running) CompanionAction.CaptureNoticeDismissed else CompanionAction.RecheckDesktopCaptureRequested) },
            size = ButtonSize.Touch, enabled = !state.operationInProgress, loading = state.operationInProgress) {
            Text(stringResource(if (running) Res.string.capture_notice_understood else Res.string.capture_check_again))
        } },
        dismissButton = { if (!running) KNetButton({ onAction(CompanionAction.CaptureNoticeDismissed) },
            size = ButtonSize.Touch, variant = ButtonVariant.Ghost, enabled = !state.operationInProgress) { Text(stringResource(Res.string.cancel)) } },
    )
}
