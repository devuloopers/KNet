package com.devuloopers.knet.companion.sharedui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIViewController

/**
 * Creates the UIKit host for the shared companion interface.
 *
 * The future iOS product owns the supplied state stream and its lifecycle. Keeping that composition outside this
 * UI module prevents the shared interface from constructing repositories, transports, or process-scoped services.
 *
 * @param state immutable ViewModel state observed by the shared UI.
 * @param onAction forwards shared user intents to the product-owned ViewModel.
 * @param onExitRequested lets the iOS product dismiss its root surface.
 * @param certificateInstallationGuidance iOS-owned installation instructions without UIKit values.
 * @return a UIKit controller hosting the Compose Multiplatform interface.
 */
public fun KNetCompanionViewController(
    state: StateFlow<CompanionUiState>,
    onAction: (CompanionAction) -> Unit,
    onExitRequested: () -> Unit,
    certificateInstallationGuidance: CertificateInstallationGuidance,
): UIViewController =
    ComposeUIViewController {
        val currentState by state.collectAsState()
        KNetCompanionApp(
            state = currentState,
            onAction = onAction,
            onExitRequested = onExitRequested,
            certificateInstallationGuidance = certificateInstallationGuidance,
        )
    }
