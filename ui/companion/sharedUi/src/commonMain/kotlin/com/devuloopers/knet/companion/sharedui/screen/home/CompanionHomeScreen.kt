package com.devuloopers.knet.companion.sharedui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionOnboardingScaffold
import com.devuloopers.knet.companion.sharedui.component.CompanionStatusRow
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_trusted
import com.devuloopers.knet.companion.sharedui.generated.resources.connect_action
import com.devuloopers.knet.companion.sharedui.generated.resources.disconnect_action
import com.devuloopers.knet.companion.sharedui.generated.resources.forget_desktop
import com.devuloopers.knet.companion.sharedui.generated.resources.home_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.home_title
import com.devuloopers.knet.companion.sharedui.generated.resources.network_ready
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unavailable
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unknown
import com.devuloopers.knet.companion.sharedui.generated.resources.refresh_credential
import com.devuloopers.knet.companion.sharedui.generated.resources.start_inspection
import com.devuloopers.knet.companion.sharedui.generated.resources.status_certificate
import com.devuloopers.knet.companion.sharedui.generated.resources.status_connected
import com.devuloopers.knet.companion.sharedui.generated.resources.status_connecting
import com.devuloopers.knet.companion.sharedui.generated.resources.status_connection
import com.devuloopers.knet.companion.sharedui.generated.resources.status_desktop
import com.devuloopers.knet.companion.sharedui.generated.resources.status_disconnected
import com.devuloopers.knet.companion.sharedui.generated.resources.status_failed
import com.devuloopers.knet.companion.sharedui.generated.resources.status_inspection
import com.devuloopers.knet.companion.sharedui.generated.resources.status_network
import com.devuloopers.knet.companion.sharedui.generated.resources.status_preparing
import com.devuloopers.knet.companion.sharedui.generated.resources.status_running
import com.devuloopers.knet.companion.sharedui.generated.resources.status_stopped
import com.devuloopers.knet.companion.sharedui.generated.resources.stop_inspection
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.card.KNetCard
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Ready screen for durable pairing, connection, credential, and inspection controls. */
@Composable
internal fun CompanionHomeScreen(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    val connectionText = when (state.connection) {
        is CompanionConnectionState.Connected -> stringResource(Res.string.status_connected)
        is CompanionConnectionState.Connecting,
        is CompanionConnectionState.Reconnecting,
        -> stringResource(Res.string.status_connecting)
        CompanionConnectionState.Disconnected -> stringResource(Res.string.status_disconnected)
        is CompanionConnectionState.Failed -> stringResource(Res.string.status_failed)
    }
    val networkText = when (state.network) {
        is CompanionNetworkState.Available -> stringResource(Res.string.network_ready)
        CompanionNetworkState.Unavailable -> stringResource(Res.string.network_unavailable)
        CompanionNetworkState.Unknown -> stringResource(Res.string.network_unknown)
    }
    val inspectionText = when (state.inspection) {
        is CompanionInspectionState.Running -> stringResource(Res.string.status_running)
        CompanionInspectionState.Stopped -> stringResource(Res.string.status_stopped)
        CompanionInspectionState.Preparing,
        CompanionInspectionState.AwaitingVpnConsent,
        CompanionInspectionState.Stopping,
        -> stringResource(Res.string.status_preparing)
        is CompanionInspectionState.Failed -> stringResource(Res.string.status_failed)
    }
    val connected = state.connection is CompanionConnectionState.Connected
    val running = state.inspection is CompanionInspectionState.Running
    val activeRegistration = state.activeRegistration

    CompanionOnboardingScaffold(
        title = stringResource(Res.string.home_title),
        summary = stringResource(Res.string.home_summary),
        currentStep = 3,
        state = state,
        onAction = onAction,
    ) {
        KNetCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
                CompanionStatusRow(
                    label = stringResource(Res.string.status_desktop),
                    value = activeRegistration?.desktopDisplayName.orEmpty(),
                    icon = KNetIcons.Info,
                    positive = activeRegistration != null,
                )
                CompanionStatusRow(
                    label = stringResource(Res.string.status_network),
                    value = networkText,
                    icon = KNetIcons.Info,
                    positive = state.network is CompanionNetworkState.Available,
                )
                CompanionStatusRow(
                    label = stringResource(Res.string.status_connection),
                    value = connectionText,
                    icon = if (connected) KNetIcons.Check else KNetIcons.Warning,
                    positive = connected,
                )
                CompanionStatusRow(
                    label = stringResource(Res.string.status_certificate),
                    value = stringResource(Res.string.certificate_trusted),
                    icon = KNetIcons.Check,
                    positive = state.certificate is CompanionCertificateState.Trusted,
                )
                CompanionStatusRow(
                    label = stringResource(Res.string.status_inspection),
                    value = inspectionText,
                    icon = if (running) KNetIcons.Check else KNetIcons.Info,
                    positive = running,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
            KNetButton(
                onClick = {
                    onAction(
                        if (running) CompanionAction.StopInspectionRequested
                        else CompanionAction.StartInspectionRequested,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                loading = state.operationInProgress,
            ) {
                Text(stringResource(if (running) Res.string.stop_inspection else Res.string.start_inspection))
            }
            KNetButton(
                onClick = {
                    onAction(
                        if (connected) CompanionAction.DisconnectRequested else CompanionAction.ConnectRequested,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Secondary,
                enabled = !state.operationInProgress,
            ) {
                Text(stringResource(if (connected) Res.string.disconnect_action else Res.string.connect_action))
            }
            KNetButton(
                onClick = { onAction(CompanionAction.RefreshCredentialRequested) },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                enabled = !state.operationInProgress,
            ) { Text(stringResource(Res.string.refresh_credential)) }
            activeRegistration?.let { registration ->
                KNetButton(
                    onClick = { onAction(CompanionAction.ForgetDesktopRequested(registration.desktopId)) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Danger,
                    enabled = !state.operationInProgress,
                ) { Text(stringResource(Res.string.forget_desktop)) }
            }
        }
    }
}
