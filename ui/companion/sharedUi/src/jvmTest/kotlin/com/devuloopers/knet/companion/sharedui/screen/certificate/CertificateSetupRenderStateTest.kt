package com.devuloopers.knet.companion.sharedui.screen.certificate

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
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
            activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.InstallationRequired,
        ).toCertificateSetupRenderState()

        assertEquals(CertificateSetupPhase.Download, renderState.phase)
        assertFalse(renderState.busy)
    }

    @Test
    fun savingKeepsDownloadPhaseStableAndBusy() {
        val renderState = CompanionUiState(
            activeRegistration = REGISTRATION,
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
            activeRegistration = REGISTRATION,
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
            activeRegistration = REGISTRATION,
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
            activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.Trusted(Sha256Fingerprint("a".repeat(64)), 1_000L),
            certificateExport = saved,
        ).toCertificateSetupRenderState()

        assertIs<CertificateSetupPhase.Verified>(renderState.phase)
        assertIs<CertificateVerificationRenderState.Trusted>(renderState.verification)
        assertTrue(renderState.canContinue)
    }

    @Test
    fun unknownAndVerifyingTrustShowCheckingWithoutADownloadAction() {
        for (certificate in listOf(CompanionCertificateState.Unknown, CompanionCertificateState.Verifying)) {
            val state = CompanionUiState(activeRegistration = REGISTRATION, certificate = certificate)
                .toCertificateSetupRenderState()
            assertEquals(CertificateSetupPhase.Checking, state.phase)
            assertTrue(state.busy)
            assertFalse(state.canContinue)
        }
    }

    @Test
    fun alreadyTrustedRootEnablesContinueWithoutAnExport() {
        val state = CompanionUiState(activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.Trusted(REGISTRATION.rootCertificateSha256, 1_000L))
            .toCertificateSetupRenderState()
        assertEquals(null, assertIs<CertificateSetupPhase.Verified>(state.phase).savedExport)
        assertTrue(state.canContinue)
    }

    @Test
    fun failedAndDeferredChecksRequireRetryWithoutAssumingInstallationIsMissing() {
        val failure = CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Desktop unavailable.", true)
        for (certificate in listOf(CompanionCertificateState.VerificationDeferred(failure),
            CompanionCertificateState.Rejected(failure))) {
            val state = CompanionUiState(activeRegistration = REGISTRATION, certificate = certificate)
                .toCertificateSetupRenderState()
            assertEquals(CertificateSetupPhase.VerificationUnavailable, state.phase)
            assertFalse(state.busy)
            assertFalse(state.canContinue)
        }
    }

    @Test
    fun wrongOrUnselectedRootNeverEnablesContinue() {
        val trust = CompanionCertificateState.Trusted(Sha256Fingerprint("b".repeat(64)), 1_000L)
        for (registration in listOf(REGISTRATION, null)) {
            val state = CompanionUiState(activeRegistration = registration, certificate = trust)
                .toCertificateSetupRenderState()
            assertEquals(CertificateSetupPhase.VerificationUnavailable, state.phase)
            assertIs<CertificateVerificationRenderState.Rejected>(state.verification)
            assertFalse(state.canContinue)
        }
    }

    @Test
    fun exportForAnotherDesktopCannotSupplyInstallationOrVerifiedDetails() {
        val saved = CompanionCertificateExportState.Saved(CompanionDesktopId("other-desktop"), "other.crt", "Downloads")
        val state = CompanionUiState(activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.InstallationRequired, certificateExport = saved)
        assertEquals(CertificateSetupPhase.Download, state.toCertificateSetupRenderState().phase)
        val trusted = state.copy(certificate = CompanionCertificateState.Trusted(REGISTRATION.rootCertificateSha256, 1_000L))
        assertEquals(null, assertIs<CertificateSetupPhase.Verified>(trusted.toCertificateSetupRenderState().phase).savedExport)
    }

    @Test
    fun postDownloadRechecksRetainInstallationUntilTrustIsProvenAndRemovalRequiresSetupAgain() {
        val saved = CompanionCertificateExportState.Saved(DESKTOP_ID, "root.crt", "Downloads")
        val state = CompanionUiState(activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.Verifying, certificateExport = saved)
        assertIs<CertificateSetupPhase.Installation>(state.toCertificateSetupRenderState().phase)
        assertTrue(state.toCertificateSetupRenderState().busy)
        val trusted = state.copy(certificate = CompanionCertificateState.Trusted(REGISTRATION.rootCertificateSha256, 1_000L))
        assertTrue(trusted.toCertificateSetupRenderState().canContinue)
        assertIs<CertificateSetupPhase.Installation>(trusted.copy(certificate = CompanionCertificateState.InstallationRequired)
            .toCertificateSetupRenderState().phase)
    }

    @Test
    fun enrollmentSaveFailureAllowsRetryButPendingCompletionDisablesContinue() {
        val state = CompanionUiState(activeRegistration = REGISTRATION,
            certificate = CompanionCertificateState.Trusted(REGISTRATION.rootCertificateSha256, 1_000L),
            failure = CompanionFailure(CompanionFailureCode.PERSISTENCE_FAILED, "Unable to save setup.", true))
        assertTrue(state.toCertificateSetupRenderState().canContinue)
        assertIs<CertificateVerificationRenderState.Failed>(state.toCertificateSetupRenderState().verification)
        assertFalse(state.copy(operationInProgress = true).toCertificateSetupRenderState().canContinue)
    }

    private companion object {
        val DESKTOP_ID: CompanionDesktopId = CompanionDesktopId("desktop-1")
        val REGISTRATION = certificateTestRegistration()
    }
}
