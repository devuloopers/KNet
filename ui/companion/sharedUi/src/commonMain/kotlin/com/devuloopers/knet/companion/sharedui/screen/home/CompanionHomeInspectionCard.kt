package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionHomeFailureNotice
import com.devuloopers.knet.companion.presentation.state.CompanionHomeInspectionControl
import com.devuloopers.knet.companion.presentation.state.CompanionHomeUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetTextButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompanionInspectionControlCard(state: CompanionHomeUiState, onAction: (CompanionAction) -> Unit) {
    val control = state.inspectionControl
    val animationDuration = homeAnimationDuration()
    val running = control == CompanionHomeInspectionControl.Stop
    val loading = control == CompanionHomeInspectionControl.Starting || control == CompanionHomeInspectionControl.Stopping
    val enabled = when (control) {
        is CompanionHomeInspectionControl.Start -> control.enabled
        is CompanionHomeInspectionControl.Retry -> control.enabled
        CompanionHomeInspectionControl.Starting, CompanionHomeInspectionControl.Stopping -> false
        else -> true
    }
    KNetSurface(
        modifier = Modifier.fillMaxWidth().widthIn(max = HomeContentWidth),
        color = KNetTheme.colors.surface,
        shape = KNetTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeIconTile(KNetIcons.Shield, tunnelColor(state.tunnelStatus))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                    Text(
                        text = stringResource(Res.string.home_inspection_label),
                        style = KNetTheme.typography.titleMedium,
                        color = KNetTheme.colors.accent,
                    )
                    AnimatedContent(
                        targetState = control,
                        transitionSpec = {
                            fadeIn(tween(animationDuration)) togetherWith fadeOut(tween(animationDuration))
                        },
                        label = "CompanionHomeInspectionStatus",
                    ) { currentControl ->
                        Text(
                            text = stringResource(inspectionStatus(currentControl)),
                            style = KNetTheme.typography.heading,
                            color = KNetTheme.colors.textPrimary,
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = control,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                transitionSpec = {
                    fadeIn(tween(animationDuration)) togetherWith fadeOut(tween(animationDuration))
                },
                label = "CompanionHomeInspectionSummary",
            ) { currentControl ->
                Text(
                    text = stringResource(inspectionSummary(currentControl)),
                    style = KNetTheme.typography.bodyLarge,
                    color = KNetTheme.colors.textSecondary,
                )
            }
            KNetButton(
                onClick = {
                    onAction(
                        when (control) {
                            CompanionHomeInspectionControl.Stop -> CompanionAction.StopInspectionRequested
                            CompanionHomeInspectionControl.ContinueVpnSetup -> CompanionAction.VpnConsentRequested
                            else -> CompanionAction.StartInspectionRequested
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Touch,
                variant = if (running) ButtonVariant.Secondary else ButtonVariant.Primary,
                enabled = enabled,
                loading = loading,
            ) {
                Icon(
                    imageVector = when {
                        running -> KNetIcons.Pause
                        control is CompanionHomeInspectionControl.Retry -> KNetIcons.Refresh
                        else -> KNetIcons.Play
                    },
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(KNetTheme.spacing.sm))
                Text(stringResource(inspectionButtonLabel(control)))
            }
            AnimatedVisibility(
                visible = state.failureNotice != null,
                enter = fadeIn(tween(animationDuration)),
                exit = fadeOut(tween(animationDuration)),
            ) {
                state.failureNotice?.let { notice ->
                    HomeFailureNotice(
                        message = notice.failure.message,
                        onDismiss = if (notice is CompanionHomeFailureNotice.Dismissible) {
                            { onAction(CompanionAction.ClearFailure) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFailureNotice(message: String, onDismiss: (() -> Unit)?) {
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = KNetTheme.colors.semantic.errorContainer,
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.semantic.error.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KNetIcons.Warning, null, tint = KNetTheme.colors.semantic.error, modifier = Modifier.size(24.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = KNetTheme.typography.bodyMedium,
                color = KNetTheme.colors.textPrimary,
            )
            onDismiss?.let { dismiss ->
                KNetTextButton(onClick = dismiss) { Text(stringResource(Res.string.dismiss)) }
            }
        }
    }
}

private fun inspectionStatus(value: CompanionHomeInspectionControl): StringResource = when (value) {
    is CompanionHomeInspectionControl.Start -> Res.string.home_inspection_not_running
    CompanionHomeInspectionControl.Starting, CompanionHomeInspectionControl.ContinueVpnSetup -> Res.string.home_inspection_starting
    CompanionHomeInspectionControl.Stop -> Res.string.home_inspection_running
    CompanionHomeInspectionControl.Stopping -> Res.string.home_inspection_stopping
    is CompanionHomeInspectionControl.Retry -> Res.string.home_inspection_failed
}

private fun inspectionSummary(value: CompanionHomeInspectionControl): StringResource = when (value) {
    is CompanionHomeInspectionControl.Start -> Res.string.home_inspection_ready_summary
    CompanionHomeInspectionControl.Stop -> Res.string.home_inspection_running_summary
    is CompanionHomeInspectionControl.Retry -> Res.string.home_inspection_failed_summary
    else -> Res.string.home_inspection_busy_summary
}

private fun inspectionButtonLabel(value: CompanionHomeInspectionControl): StringResource = when (value) {
    CompanionHomeInspectionControl.Stop, CompanionHomeInspectionControl.Stopping -> Res.string.stop_inspection
    CompanionHomeInspectionControl.ContinueVpnSetup -> Res.string.continue_vpn_setup
    is CompanionHomeInspectionControl.Retry -> Res.string.retry
    else -> Res.string.start_inspection
}
