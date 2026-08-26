package com.devuloopers.knet.companion.sharedui.screen.inspection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.component.CompanionOnboardingScaffold
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.allow_inspection
import com.devuloopers.knet.companion.sharedui.generated.resources.inspection_detail_body
import com.devuloopers.knet.companion.sharedui.generated.resources.inspection_detail_title
import com.devuloopers.knet.companion.sharedui.generated.resources.inspection_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.inspection_title
import com.devuloopers.knet.companion.sharedui.generated.resources.not_now
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.card.KNetCard
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Shared explanation shown immediately before the native VPN consent surface. */
@Composable
internal fun InspectionPermissionScreen(state: CompanionUiState, onAction: (CompanionAction) -> Unit) {
    CompanionOnboardingScaffold(
        title = stringResource(Res.string.inspection_title),
        summary = stringResource(Res.string.inspection_summary),
        currentStep = 2,
        state = state,
        onAction = onAction,
    ) {
        KNetCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                Text(
                    text = stringResource(Res.string.inspection_detail_title),
                    style = KNetTheme.typography.titleMedium,
                    color = KNetTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.inspection_detail_body),
                    style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.textSecondary,
                )
                KNetButton(
                    onClick = { onAction(CompanionAction.VpnConsentRequested) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.operationInProgress,
                ) { Text(stringResource(Res.string.allow_inspection)) }
                KNetButton(
                    onClick = { onAction(CompanionAction.InspectionPermissionDismissed) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.Ghost,
                ) { Text(stringResource(Res.string.not_now)) }
            }
        }
    }
}
