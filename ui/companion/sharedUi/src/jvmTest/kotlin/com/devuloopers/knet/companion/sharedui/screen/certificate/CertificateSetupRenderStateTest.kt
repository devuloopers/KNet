package com.devuloopers.knet.companion.sharedui.screen.certificate

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CertificateSetupRenderStateTest {
    @Test
    fun idleExportRendersDownloadPhase() {
        val renderState = CompanionUiState(
            certificate = CompanionCertificateState.InstallationRequired,
        ).toCertificateSetupRenderState()

        assertEquals(CertificateSetupPhase.Download, renderState.phase)
        assertFalse(renderState.busy)
    }

    @Test
    fun savingKeepsDownloadPhaseStableAndBusy() {
        val renderState = CompanionUiState(
            certificate = CompanionCertificateState.InstallationRequired,
            certificateExport = CompanionCertificateExportState.Saving(DESKTOP_ID),
        ).toCertificateSetupRenderState()

        assertEquals(CertificateSetupPhase.Download, renderState.phase)
        assertTrue(renderState.downloadInProgress)
        assertTrue(renderState.busy)
    }

    @Test
    fun savedExportMovesToInstallationPhase() {
        val saved = CompanionCertificateExportState.Saved(
            desktopId = DESKTOP_ID,
            fileName = "knet-root-ca.crt",
            locationDescription = "Downloads",
        )

        val renderState = CompanionUiState(
            certificate = CompanionCertificateState.InstallationRequired,
            certificateExport = saved,
        ).toCertificateSetupRenderState()

        val phase = assertIs<CertificateSetupPhase.Installation>(renderState.phase)
        assertEquals(saved, phase.savedExport)
        assertIs<CertificateVerificationRenderState.Waiting>(renderState.verification)
        assertFalse(renderState.canContinue)
        assertFalse(renderState.busy)
    }

    @Test
    fun certificateVerificationDisablesCertificateActions() {
        val renderState = CompanionUiState(
            certificate = CompanionCertificateState.Verifying,
        ).toCertificateSetupRenderState()

        assertTrue(renderState.verificationInProgress)
        assertTrue(renderState.busy)
    }

    @Test
    fun onlyAuthoritativelyTrustedCertificateEnablesContinue() {
        val saved = CompanionCertificateExportState.Saved(
            desktopId = DESKTOP_ID,
            fileName = "knet-root-ca.crt",
            locationDescription = "Downloads",
        )
        val renderState = CompanionUiState(
            certificate = CompanionCertificateState.Trusted(Sha256Fingerprint("a".repeat(64)), 1_000L),
            certificateExport = saved,
        ).toCertificateSetupRenderState()

        assertIs<CertificateSetupPhase.Verified>(renderState.phase)
        assertIs<CertificateVerificationRenderState.Trusted>(renderState.verification)
        assertTrue(renderState.canContinue)
    }

    private companion object {
        val DESKTOP_ID: CompanionDesktopId = CompanionDesktopId("desktop-1")
    }
}
