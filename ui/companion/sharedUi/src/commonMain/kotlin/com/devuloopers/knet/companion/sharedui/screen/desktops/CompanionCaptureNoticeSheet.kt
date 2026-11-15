package com.devuloopers.knet.companion.sharedui.screen.desktops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionActionButton
import com.devuloopers.knet.companion.sharedui.component.CompanionHeading
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Shared Android/iOS capture-readiness sheet, driven only by the selected desktop's existing notice.
 * The standard modal owns slide/drag/back/scrim dismissal and native insets. Guidance scrolls at
 * compact heights or larger text scales; presentation never starts desktop capture itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanionCaptureNoticeSheet(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    if (state.desktopCaptureNotice == null) return
    val running = state.inspection is CompanionInspectionState.Running
    val title = stringResource(if (running) Res.string.capture_paused_title else Res.string.capture_start_title)
    val message = stringResource(
        if (running) Res.string.capture_paused_message else Res.string.capture_start_message,
        state.activeRegistration?.desktopDisplayName?.value.orEmpty(),
    )
    val dismiss = { onAction(CompanionAction.CaptureNoticeDismissed) }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = KNetTheme.shapes.extraLarge,
        containerColor = KNetTheme.colors.surfaceVariant,
        contentColor = KNetTheme.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                start = KNetTheme.spacing.xxl,
                end = KNetTheme.spacing.xxl,
                bottom = KNetTheme.spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        ) {
            Icon(
                imageVector = KNetIcons.Desktop,
                contentDescription = null,
                modifier = Modifier.size(KNetTheme.dimensions.iconSizeLarge),
                tint = KNetTheme.colors.accent,
            )
            CompanionHeading(title = title, summary = message)
            CompanionActionButton(
                text = stringResource(if (running) Res.string.capture_notice_understood else Res.string.capture_check_again),
                onClick = { onAction(if (running) CompanionAction.CaptureNoticeDismissed else CompanionAction.RecheckDesktopCaptureRequested) },
                enabled = !state.operationInProgress,
                loading = !running && state.operationInProgress,
            )
            if (!running) CompanionActionButton(
                text = stringResource(Res.string.not_now),
                onClick = dismiss,
                variant = ButtonVariant.Ghost,
                enabled = !state.operationInProgress,
            )
        }
    }
}
