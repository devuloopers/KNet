package com.devuloopers.knet.companion.sharedui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.navigation.CompanionNavHost
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.scanner.UnavailableCompanionInvitationScanner
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode

/**
 * Root companion UI rendered by every Compose Multiplatform product.
 *
 * @param state immutable state supplied by the product-owned companion ViewModel.
 * @param onAction dispatches portable user intents to that ViewModel.
 * @param onExitRequested lets the native host finish or dismiss its root surface.
 * @param invitationScanner product-owned camera capability used by the shared scanner stage.
 * @param certificateInstallationGuidance product-owned native Settings instructions rendered without native types.
 * @param modifier outer layout modifier.
 */
@Composable
public fun KNetCompanionApp(
    modifier: Modifier = Modifier,
    state: CompanionUiState,
    onAction: (CompanionAction) -> Unit,
    onExitRequested: () -> Unit,
    certificateInstallationGuidance: CertificateInstallationGuidance,
    invitationScanner: CompanionInvitationScanner = UnavailableCompanionInvitationScanner
) {
    KNetTheme(themeMode = ThemeMode.System) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CompanionNavHost(
                state = state,
                onAction = onAction,
                onExitRequested = onExitRequested,
                invitationScanner = invitationScanner,
                certificateInstallationGuidance = certificateInstallationGuidance,
            )
        }
    }
}
