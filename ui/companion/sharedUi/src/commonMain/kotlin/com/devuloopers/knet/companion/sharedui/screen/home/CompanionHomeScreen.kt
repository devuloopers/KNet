package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionBrandHeader
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.continue_vpn_setup
import com.devuloopers.knet.companion.sharedui.generated.resources.home_status_failed
import com.devuloopers.knet.companion.sharedui.generated.resources.home_status_preparing
import com.devuloopers.knet.companion.sharedui.generated.resources.home_status_running
import com.devuloopers.knet.companion.sharedui.generated.resources.home_status_stopped
import com.devuloopers.knet.companion.sharedui.generated.resources.home_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.home_title
import com.devuloopers.knet.companion.sharedui.generated.resources.start_inspection
import com.devuloopers.knet.companion.sharedui.generated.resources.stop_inspection
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Functional inspection home shell reached after verified setup; its visual redesign can evolve independently. */
@Composable
internal fun CompanionHomeScreen(
    state: CompanionUiState,
    onAction: (CompanionAction) -> Unit,
) {
    val running = state.inspection is CompanionInspectionState.Running
    val busy = state.inspection == CompanionInspectionState.Preparing ||
        state.inspection == CompanionInspectionState.AwaitingVpnConsent ||
        state.inspection == CompanionInspectionState.Stopping ||
        state.operationInProgress
    val status = when (state.inspection) {
        CompanionInspectionState.Stopped -> stringResource(Res.string.home_status_stopped)
        CompanionInspectionState.Preparing,
        CompanionInspectionState.AwaitingVpnConsent,
        CompanionInspectionState.Stopping,
        -> stringResource(Res.string.home_status_preparing)
        is CompanionInspectionState.Running -> stringResource(Res.string.home_status_running)
        is CompanionInspectionState.Failed -> stringResource(Res.string.home_status_failed)
    }
    val statusColor = when (state.inspection) {
        is CompanionInspectionState.Running -> KNetTheme.colors.semantic.success
        is CompanionInspectionState.Failed -> KNetTheme.colors.semantic.error
        else -> KNetTheme.colors.accent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KNetTheme.colors.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KNetTheme.spacing.lg, vertical = KNetTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xxl),
        ) {
            CompanionBrandHeader(modifier = Modifier.widthIn(max = 560.dp))
            KNetSurface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                color = KNetTheme.colors.surface,
                shape = KNetTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, KNetTheme.colors.border),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xl),
                ) {
                    Text(
                        text = stringResource(Res.string.home_title),
                        style = KNetTheme.typography.hero,
                        color = KNetTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.home_summary),
                        style = KNetTheme.typography.bodyLarge,
                        color = KNetTheme.colors.textSecondary,
                    )
                    InspectionStatusRow(
                        desktopName = state.activeRegistration?.desktopDisplayName?.value.orEmpty(),
                        status = status,
                        statusColor = statusColor,
                    )
                    KNetButton(
                        onClick = {
                            onAction(
                                when {
                                    running -> CompanionAction.StopInspectionRequested
                                    state.inspectionPermissionRequired -> CompanionAction.VpnConsentRequested
                                    else -> CompanionAction.StartInspectionRequested
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.Touch,
                        loading = busy,
                    ) {
                        Icon(
                            imageVector = if (running) KNetIcons.Pause else KNetIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(KNetTheme.spacing.sm))
                        Text(
                            text = stringResource(
                                when {
                                    running -> Res.string.stop_inspection
                                    state.inspectionPermissionRequired -> Res.string.continue_vpn_setup
                                    else -> Res.string.start_inspection
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectionStatusRow(
    desktopName: String,
    status: String,
    statusColor: Color,
) {
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = KNetTheme.colors.surfaceVariant.copy(alpha = 0.58f),
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = KNetIcons.Shield,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(34.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = status, style = KNetTheme.typography.heading, color = statusColor)
                Text(text = desktopName, style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textSecondary)
            }
        }
    }
}
