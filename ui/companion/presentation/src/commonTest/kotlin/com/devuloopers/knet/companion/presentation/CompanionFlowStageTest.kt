package com.devuloopers.knet.companion.presentation

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionCertificateEnrollment
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.presentation.flow.CompanionFlowStage
import com.devuloopers.knet.companion.presentation.flow.resolveFlowStage
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionFlowStageTest {
    @Test
    fun `pairing gates take priority over certificate and permission state`() {
        assertEquals(CompanionFlowStage.CONNECT_DESKTOP, CompanionUiState().resolveFlowStage())
        assertEquals(
            CompanionFlowStage.CONNECT_DESKTOP,
            CompanionUiState(invitationScannerVisible = true).resolveFlowStage(),
        )
    }

    @Test
    fun `active registration remains on certificate until onboarding completion is persisted`() {
        val registration = registration()
        val trusted = CompanionCertificateState.Trusted(registration.rootCertificateSha256, 2_000L)

        assertEquals(
            CompanionFlowStage.CERTIFICATE_SETUP,
            CompanionUiState(activeRegistration = registration).resolveFlowStage(),
        )
        assertEquals(
            CompanionFlowStage.CERTIFICATE_SETUP,
            CompanionUiState(
                activeRegistration = registration,
                certificate = trusted,
                inspectionPermissionRequired = true,
            ).resolveFlowStage(),
        )
        assertEquals(
            CompanionFlowStage.CERTIFICATE_SETUP,
            CompanionUiState(activeRegistration = registration, certificate = trusted).resolveFlowStage(),
        )
    }

    @Test
    fun `matching durable enrollment restores home independently from transient verification`() {
        val registration = registration()
        val trusted = CompanionCertificateState.Trusted(registration.rootCertificateSha256, 2_000L)
        val enrollment = CompanionCertificateEnrollment(
            registration.desktopId,
            registration.rootCertificateSha256,
            completedAtEpochMillis = 2_000L,
        )

        assertEquals(
            CompanionFlowStage.CERTIFICATE_SETUP,
            CompanionUiState(
                activeRegistration = registration,
                certificate = trusted,
            ).resolveFlowStage(),
        )
        assertEquals(
            CompanionFlowStage.INSPECTION_HOME,
            CompanionUiState(
                activeRegistration = registration,
                certificate = CompanionCertificateState.Verifying,
                certificateEnrollment = enrollment,
            ).resolveFlowStage(),
        )
    }

    @Test
    fun `enrollment for a previous root cannot bypass certificate setup`() {
        val registration = registration()

        assertEquals(
            CompanionFlowStage.CERTIFICATE_SETUP,
            CompanionUiState(
                activeRegistration = registration,
                certificateEnrollment = CompanionCertificateEnrollment(
                    registration.desktopId,
                    Sha256Fingerprint("c".repeat(64)),
                    completedAtEpochMillis = 2_000L,
                ),
            ).resolveFlowStage(),
        )
    }

    private fun registration(): CompanionRegistration = CompanionRegistration(
        desktopId = CompanionDesktopId("desktop-1"),
        desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
        deviceId = RegisteredDeviceId("device-1"),
        controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
        proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, CompanionEndpointScheme.HTTPS),
        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
        credentialReference = CompanionCredentialReference("credential-reference"),
        scopes = setOf(DeviceScope.PROXY_STREAM),
        pairedAtEpochMillis = 1_000L,
        credentialExpiresAtEpochMillis = 5_000L,
    )
}
