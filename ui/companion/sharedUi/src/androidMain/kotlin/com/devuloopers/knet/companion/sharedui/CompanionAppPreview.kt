package com.devuloopers.knet.companion.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance

/** Android Studio preview for the shared companion screen. */
@Preview(showBackground = true)
@Composable
private fun CompanionAppPreview() {
    KNetCompanionApp(
        state = CompanionUiState(),
        onAction = {},
        onExitRequested = {},
        certificateInstallationGuidance = CertificateInstallationGuidance(
            title = "Install the downloaded CA certificate",
            steps = listOf("Open security settings.", "Select the downloaded KNet certificate."),
        ),
    )
}
