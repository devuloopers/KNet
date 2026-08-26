package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionOnboardingScaffold
import com.devuloopers.knet.companion.sharedui.component.CompanionStatusRow
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.connect_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.connect_title
import com.devuloopers.knet.companion.sharedui.generated.resources.continue_action
import com.devuloopers.knet.companion.sharedui.generated.resources.import_qr
import com.devuloopers.knet.companion.sharedui.generated.resources.scan_qr
import com.devuloopers.knet.companion.sharedui.generated.resources.invitation_help
import com.devuloopers.knet.companion.sharedui.generated.resources.invitation_label
import com.devuloopers.knet.companion.sharedui.generated.resources.invitation_placeholder
import com.devuloopers.knet.companion.sharedui.generated.resources.network_ready
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unavailable
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unknown
import com.devuloopers.knet.companion.sharedui.generated.resources.paired_desktops
import com.devuloopers.knet.companion.sharedui.generated.resources.status_network
import com.devuloopers.knet.companion.sharedui.generated.resources.use_desktop
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.card.KNetCard
import com.devuloopers.knet.ui.core.components.input.KNetMultilineField
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Invitation entry screen that does not persist secret-bearing payload text. */
@Composable
internal fun ConnectDesktopScreen(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    var invitation by rememberSaveable { mutableStateOf("") }
    val networkLabel = when (state.network) {
        is CompanionNetworkState.Available -> stringResource(Res.string.network_ready)
        CompanionNetworkState.Unavailable -> stringResource(Res.string.network_unavailable)
        CompanionNetworkState.Unknown -> stringResource(Res.string.network_unknown)
    }
    CompanionOnboardingScaffold(
        title = stringResource(Res.string.connect_title),
        summary = stringResource(Res.string.connect_summary),
        currentStep = 0,
        state = state,
        onAction = onAction,
    ) {
        CompanionStatusRow(
            label = stringResource(Res.string.status_network),
            value = networkLabel,
            icon = KNetIcons.Info,
            positive = state.network is CompanionNetworkState.Available,
        )
        KNetCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                Text(
                    text = stringResource(Res.string.invitation_label),
                    style = KNetTheme.typography.titleMedium,
                    color = KNetTheme.colors.textPrimary,
                )
                KNetMultilineField(
                    value = invitation,
                    onValueChange = { invitation = it },
                    placeholder = stringResource(Res.string.invitation_placeholder),
                    enabled = !state.operationInProgress,
                )
                Text(
                    text = stringResource(Res.string.invitation_help),
                    style = KNetTheme.typography.caption,
                    color = KNetTheme.colors.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KNetButton(
                        onClick = { onAction(CompanionAction.ScanInvitationRequested) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(Res.string.scan_qr)) }
                    KNetButton(
                        onClick = { onAction(CompanionAction.ImportInvitationImageRequested) },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Secondary,
                    ) { Text(stringResource(Res.string.import_qr)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    KNetButton(
                        onClick = { onAction(CompanionAction.InvitationSubmitted(invitation.trim())) },
                        enabled = invitation.isNotBlank() && !state.operationInProgress,
                    ) { Text(stringResource(Res.string.continue_action)) }
                }
            }
        }
        if (state.registrations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                Text(
                    text = stringResource(Res.string.paired_desktops),
                    style = KNetTheme.typography.titleMedium,
                    color = KNetTheme.colors.textPrimary,
                )
                state.registrations.forEach { registration ->
                    KNetCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = registration.desktopDisplayName,
                                style = KNetTheme.typography.bodyMedium,
                                color = KNetTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f).padding(end = KNetTheme.spacing.sm),
                            )
                            KNetButton(
                                onClick = {
                                    onAction(CompanionAction.RegistrationSelected(registration.desktopId))
                                },
                                variant = ButtonVariant.Secondary,
                            ) { Text(stringResource(Res.string.use_desktop)) }
                        }
                    }
                }
            }
        }
    }
}
