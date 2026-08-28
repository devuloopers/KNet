package com.devuloopers.knet.companion.sharedui.component

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionSetupProgressTest {
    @Test
    fun unpairedStateRemainsAtScanningMilestone() {
        assertEquals(
            CompanionSetupProgress.Scanning,
            CompanionUiState().toCompanionSetupProgress(),
        )
    }

    @Test
    fun pairedStateAdvancesHalfwayTowardCertificateMilestone() {
        val progress = CompanionUiState(activeRegistration = REGISTRATION).toCompanionSetupProgress()

        assertEquals(CompanionSetupProgress.DesktopConnected, progress)
        assertEquals(0.5f, progress.toVisualState().firstConnectorProgress)
    }

    @Test
    fun savedCertificateAdvancesToCertificateMilestone() {
        val progress = CompanionUiState(
            activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.InstallationRequired,
            certificateExport = SAVED_EXPORT,
        ).toCompanionSetupProgress()

        assertEquals(CompanionSetupProgress.CertificateDownloaded, progress)
        assertEquals(1f, progress.toVisualState().firstConnectorProgress)
    }

    @Test
    fun trustedDownloadedCertificateCompletesCertificateMilestone() {
        val progress = CompanionUiState(
            activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.Trusted(ROOT_FINGERPRINT, 2_000L),
            certificateExport = SAVED_EXPORT,
        ).toCompanionSetupProgress()

        assertEquals(CompanionSetupProgress.CertificateVerified, progress)
        assertEquals(1f, progress.toVisualState().secondConnectorProgress)
        assertEquals(CompanionSetupNodeState.Active, progress.toVisualState().inspection)
    }

    private companion object {
        val DESKTOP_ID = CompanionDesktopId("desktop-1")
        val ROOT_FINGERPRINT = Sha256Fingerprint("b".repeat(64))
        val REGISTRATION = CompanionRegistration(
            desktopId = DESKTOP_ID,
            desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
            deviceId = RegisteredDeviceId("device-1"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, CompanionEndpointScheme.HTTPS),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = ROOT_FINGERPRINT,
            rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
            credentialReference = CompanionCredentialReference("credential-reference"),
            scopes = setOf(DeviceScope.PROXY_STREAM),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 5_000L,
        )
        val SAVED_EXPORT = CompanionCertificateExportState.Saved(
            desktopId = DESKTOP_ID,
            fileName = "knet-root-ca.crt",
            locationDescription = "Downloads",
        )
    }
}
