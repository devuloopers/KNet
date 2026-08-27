package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode

@Preview(
    name = "Certificate - Ready to download",
    group = "Certificate",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun CertificateReadyPreview() {
    CertificatePreview(
        state = CompanionUiState(certificate = CompanionCertificateState.InstallationRequired),
    )
}

@Preview(
    name = "Certificate - Downloading",
    group = "Certificate states",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun CertificateDownloadingPreview() {
    CertificatePreview(
        state = CompanionUiState(
            certificate = CompanionCertificateState.InstallationRequired,
            certificateExport = CompanionCertificateExportState.Saving(PREVIEW_DESKTOP_ID),
        ),
    )
}

@Preview(
    name = "Certificate - Installation guidance",
    group = "Certificate states",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun CertificateInstallationPreview() {
    CertificatePreview(
        state = CompanionUiState(
            certificate = CompanionCertificateState.InstallationRequired,
            certificateExport = CompanionCertificateExportState.Saved(
                desktopId = PREVIEW_DESKTOP_ID,
                fileName = "knet-root-ca.crt",
                locationDescription = "Downloads",
            ),
        ),
    )
}

@Preview(
    name = "Certificate - Installation verified",
    group = "Certificate states",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun CertificateInstallationVerifiedPreview() {
    CertificatePreview(
        state = CompanionUiState(
            certificate = CompanionCertificateState.Trusted(Sha256Fingerprint("a".repeat(64)), 1_000L),
            certificateExport = CompanionCertificateExportState.Saved(
                desktopId = PREVIEW_DESKTOP_ID,
                fileName = "knet-root-ca.crt",
                locationDescription = "Downloads/KNet",
            ),
        ),
    )
}

@Preview(
    name = "Certificate - Recoverable failure",
    group = "Certificate states",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun CertificateFailurePreview() {
    CertificatePreview(
        state = CompanionUiState(
            certificate = CompanionCertificateState.InstallationRequired,
            failure = CompanionFailure(
                code = CompanionFailureCode.PERSISTENCE_FAILED,
                message = "The certificate could not be saved. Check available storage and try again.",
                recoverable = true,
            ),
        ),
    )
}

@Preview(
    name = "Certificate - Compact light",
    group = "Certificate responsive",
    widthDp = 320,
    heightDp = 640,
    showBackground = true,
)
@Composable
private fun CertificateCompactLightPreview() {
    CertificatePreview(
        state = CompanionUiState(certificate = CompanionCertificateState.Verifying),
        themeMode = ThemeMode.Light,
    )
}

@Composable
private fun CertificatePreview(
    state: CompanionUiState,
    themeMode: ThemeMode = ThemeMode.Dark,
) {
    KNetTheme(themeMode = themeMode) {
        CertificateSetupScreen(
            state = state,
            installationGuidance = CertificateInstallationGuidance(
                steps = listOf(
                    "Open security or credential settings.",
                    "Choose Install a CA certificate.",
                    "Select knet-root-ca.crt from Downloads.",
                ),
            ),
            onAction = {},
        )
    }
}

private val PREVIEW_DESKTOP_ID: CompanionDesktopId = CompanionDesktopId("preview-desktop")
