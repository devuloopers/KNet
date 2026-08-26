package com.devuloopers.knet.companion.sharedui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.presentation.flow.resolveFlowStage
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateSetupScreen
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance
import com.devuloopers.knet.companion.sharedui.screen.confirm.ConfirmDesktopScreen
import com.devuloopers.knet.companion.sharedui.screen.connect.ConnectDesktopScreen
import com.devuloopers.knet.companion.sharedui.screen.home.CompanionHomeScreen
import com.devuloopers.knet.companion.sharedui.screen.inspection.InspectionPermissionScreen
import com.devuloopers.knet.companion.sharedui.screen.scanner.InvitationScannerScreen
import com.devuloopers.knet.companion.sharedui.scanner.CompanionInvitationScanner

/** State-gated Navigation 3 host shared by Android and the future iOS product. */
@Composable
internal fun CompanionNavHost(
    state: CompanionUiState,
    onAction: (CompanionAction) -> Unit,
    onExitRequested: () -> Unit,
    invitationScanner: CompanionInvitationScanner,
    certificateInstallationGuidance: CertificateInstallationGuidance,
) {
    val requiredRoute = CompanionNavigator.routeFor(state.resolveFlowStage())
    val backStack = rememberNavBackStack(companionNavigationConfiguration, requiredRoute)

    LaunchedEffect(requiredRoute) {
        CompanionNavigator.reconcile(backStack, requiredRoute)
    }

    // A restored route can be stale for one composition. Rendering the required route directly prevents a
    // protected screen from flashing before the serialized back stack is reconciled.
    val visibleBackStack = if (backStack.size == 1 && backStack.lastOrNull() == requiredRoute) {
        backStack
    } else {
        listOf(requiredRoute)
    }

    NavDisplay(
        backStack = visibleBackStack,
        modifier = Modifier.fillMaxSize(),
        onBack = {
            when (requiredRoute) {
                CompanionRoute.ConfirmDesktop -> onAction(CompanionAction.InvitationDismissed)
                CompanionRoute.ScanInvitation -> onAction(CompanionAction.InvitationScannerDismissed)
                CompanionRoute.InspectionPermission -> onAction(CompanionAction.InspectionPermissionDismissed)
                else -> onExitRequested()
            }
        },
        entryProvider = entryProvider {
            entry(CompanionRoute.ConnectDesktop) {
                ConnectDesktopScreen(state = state, onAction = onAction)
            }
            entry(CompanionRoute.ScanInvitation) {
                InvitationScannerScreen(state = state, scanner = invitationScanner, onAction = onAction)
            }
            entry(CompanionRoute.ConfirmDesktop) {
                ConfirmDesktopScreen(state = state, onAction = onAction)
            }
            entry(CompanionRoute.CertificateSetup) {
                CertificateSetupScreen(
                    state = state,
                    installationGuidance = certificateInstallationGuidance,
                    onAction = onAction,
                )
            }
            entry(CompanionRoute.InspectionPermission) {
                InspectionPermissionScreen(state = state, onAction = onAction)
            }
            entry(CompanionRoute.Home) {
                CompanionHomeScreen(state = state, onAction = onAction)
            }
        },
    )
}
