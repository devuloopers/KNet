package com.devuloopers.knet.companion.presentation

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionDesktopAvailability
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionTransportKind
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.presentation.state.CompanionHomeCertificateStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeDesktopStatus
import com.devuloopers.knet.companion.presentation.state.CompanionHomeFailureNotice
import com.devuloopers.knet.companion.presentation.state.CompanionHomeHttpsCapability
import com.devuloopers.knet.companion.presentation.state.CompanionHomeInspectionControl
import com.devuloopers.knet.companion.presentation.state.CompanionHomeReadiness
import com.devuloopers.knet.companion.presentation.state.CompanionHomeTunnelStatus
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.presentation.state.toCompanionHomeUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompanionHomeUiStateTest {
    @Test
    fun restoredHomeShowsCheckingWhileLiveTrustVerificationIsPending() {
        val home = CompanionUiState(
            certificate = CompanionCertificateState.Verifying,
            network = CompanionNetworkState.Available(metered = false),
        ).toCompanionHomeUiState()

        assertEquals(CompanionHomeReadiness.CHECKING, home.readiness)
        assertEquals(false, assertIs<CompanionHomeInspectionControl.Start>(home.inspectionControl).enabled)
    }

    @Test
    fun authenticatedDesktopAndTrustedCertificateProduceReadyStartControl() {
        val home = CompanionUiState(
            certificate = CompanionCertificateState.Trusted(FINGERPRINT, 1_000L),
            network = CompanionNetworkState.Available(metered = false),
            desktopAvailability = CompanionDesktopAvailability.Available(DESKTOP_ID, 2_000L),
        ).toCompanionHomeUiState()

        assertEquals(CompanionHomeReadiness.READY, home.readiness)
        assertEquals(CompanionHomeDesktopStatus.AVAILABLE, home.desktopStatus)
        assertEquals(CompanionHomeTunnelStatus.INACTIVE, home.tunnelStatus)
        assertEquals(CompanionHomeHttpsCapability.FULL, home.httpsCapability)
        assertTrue(assertIs<CompanionHomeInspectionControl.Start>(home.inspectionControl).enabled)
    }

    @Test
    fun runningInspectionProducesActiveStopControlAndActualTransportPath() {
        val home = CompanionUiState(
            certificate = CompanionCertificateState.Trusted(FINGERPRINT, 1_000L),
            network = CompanionNetworkState.Available(metered = false),
            connection = CompanionConnectionState.Connected(DESKTOP_ID, CompanionTransportKind.DIRECT_LAN, 2_000L),
            inspection = CompanionInspectionState.Running(CompanionInspectionMode.DEVICE_VPN, 2_000L, true),
        ).toCompanionHomeUiState()

        assertEquals(CompanionHomeReadiness.ACTIVE, home.readiness)
        assertEquals(CompanionHomeTunnelStatus.ACTIVE, home.tunnelStatus)
        assertEquals(CompanionHomeInspectionControl.Stop, home.inspectionControl)
    }

    @Test
    fun unavailableDesktopNeverProducesAnEnabledStartControl() {
        val failure = CompanionFailure(
            CompanionFailureCode.TRANSPORT_UNAVAILABLE,
            "The paired desktop is unavailable.",
            true,
        )
        val home = CompanionUiState(
            certificate = CompanionCertificateState.Trusted(FINGERPRINT, 1_000L),
            network = CompanionNetworkState.Available(metered = false),
            desktopAvailability = CompanionDesktopAvailability.Unavailable(DESKTOP_ID, failure),
        ).toCompanionHomeUiState()

        assertEquals(CompanionHomeReadiness.UNAVAILABLE, home.readiness)
        assertEquals(failure, assertIs<CompanionHomeFailureNotice.Persistent>(home.failureNotice).failure)
        assertEquals(false, assertIs<CompanionHomeInspectionControl.Start>(home.inspectionControl).enabled)
    }

    @Test
    fun deferredCertificateVerificationIsNeutralWhileDesktopIsUnavailable() {
        val failure = CompanionFailure(
            CompanionFailureCode.TRANSPORT_UNAVAILABLE,
            "The paired desktop is unavailable.",
            true,
        )
        val home = CompanionUiState(
            certificate = CompanionCertificateState.VerificationDeferred(failure),
            network = CompanionNetworkState.Available(metered = false),
            desktopAvailability = CompanionDesktopAvailability.Unavailable(DESKTOP_ID, failure),
        ).toCompanionHomeUiState()

        assertEquals(CompanionHomeReadiness.UNAVAILABLE, home.readiness)
        assertEquals(
            CompanionHomeCertificateStatus.VERIFICATION_PENDING,
            home.certificateStatus,
        )
    }

    private companion object {
        val DESKTOP_ID: CompanionDesktopId = CompanionDesktopId("desktop-1")
        val FINGERPRINT: Sha256Fingerprint = Sha256Fingerprint("a".repeat(64))
    }
}
