package com.devuloopers.knet.companion.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.sharedui.screen.certificate.CertificateInstallationGuidance
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope

/** Android Studio preview for the shared companion screen. */
@Preview(showBackground = true)
@Composable
private fun CompanionAppPreview() {
    KNetCompanionApp(
        state = CompanionUiState(),
        onAction = {},
        onExitRequested = {},
        certificateInstallationGuidance = CertificateInstallationGuidance(
            steps = listOf("Open security settings.", "Select the downloaded KNet certificate."),
        ),
    )
}

/** Verified certificate completion preview for the third redesigned setup screen. */
@Preview(name = "Certificate verified", showBackground = true)
@Composable
private fun CertificateVerifiedPreview() {
    val registration = previewRegistration()
    KNetCompanionApp(
        state = CompanionUiState(
            activeRegistration = registration,
            certificate = CompanionCertificateState.Trusted(registration.rootCertificateSha256, 2_000L),
            certificateExport = CompanionCertificateExportState.Saved(
                desktopId = registration.desktopId,
                fileName = "knet-root-ca.crt",
                locationDescription = "Downloads",
            ),
        ),
        onAction = {},
        onExitRequested = {},
        certificateInstallationGuidance = CertificateInstallationGuidance(steps = emptyList()),
    )
}

private fun previewRegistration(): CompanionRegistration = CompanionRegistration(
    desktopId = CompanionDesktopId("preview-desktop"),
    desktopDisplayName = "KNet Desktop",
    deviceId = RegisteredDeviceId("preview-device"),
    controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, true),
    proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, true),
    transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
    rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
    rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
    credentialReference = CompanionCredentialReference("preview-credential"),
    scopes = setOf(DeviceScope.PROXY_STREAM),
    pairedAtEpochMillis = 1_000L,
    credentialExpiresAtEpochMillis = 5_000L,
)
