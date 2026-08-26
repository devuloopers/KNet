package com.devuloopers.knet.companion.sharedui.screen.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionOnboardingScaffold
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.back
import com.devuloopers.knet.companion.sharedui.generated.resources.confirm_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.confirm_title
import com.devuloopers.knet.companion.sharedui.generated.resources.desktop_name
import com.devuloopers.knet.companion.sharedui.generated.resources.device_name_label
import com.devuloopers.knet.companion.sharedui.generated.resources.device_name_placeholder
import com.devuloopers.knet.companion.sharedui.generated.resources.pair_action
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.card.KNetCard
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Confirmation screen for one validated invitation held only by the ViewModel. */
@Composable
internal fun ConfirmDesktopScreen(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    var deviceName by rememberSaveable { mutableStateOf("") }
    CompanionOnboardingScaffold(
        title = stringResource(Res.string.confirm_title),
        summary = stringResource(Res.string.confirm_summary),
        currentStep = 0,
        state = state,
        onAction = onAction,
    ) {
        KNetCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                Text(
                    text = stringResource(Res.string.desktop_name),
                    style = KNetTheme.typography.caption,
                    color = KNetTheme.colors.textMuted,
                )
                Text(
                    text = state.invitationDesktopName.orEmpty(),
                    style = KNetTheme.typography.titleLarge,
                    color = KNetTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.device_name_label),
                    style = KNetTheme.typography.titleMedium,
                    color = KNetTheme.colors.textPrimary,
                )
                KNetTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    config = InputFieldConfig(
                        placeholder = stringResource(Res.string.device_name_placeholder),
                    ),
                )
                KNetButton(
                    onClick = { onAction(CompanionAction.PairSubmitted(deviceName.trim())) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = deviceName.isNotBlank() && !state.pairingInProgress,
                    loading = state.pairingInProgress,
                ) { Text(stringResource(Res.string.pair_action)) }
                KNetButton(
                    onClick = { onAction(CompanionAction.InvitationDismissed) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Ghost,
                    enabled = !state.pairingInProgress,
                ) { Text(stringResource(Res.string.back)) }
            }
        }
    }
}
