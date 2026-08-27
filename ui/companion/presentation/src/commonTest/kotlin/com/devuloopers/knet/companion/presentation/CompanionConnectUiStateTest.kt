package com.devuloopers.knet.companion.presentation

import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.presentation.state.CompanionConnectFeedback
import com.devuloopers.knet.companion.presentation.state.CompanionConnectFailureKind
import com.devuloopers.knet.companion.presentation.state.CompanionConnectScanState
import com.devuloopers.knet.companion.presentation.state.CompanionConnectVisualMode
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.presentation.state.toConnectUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompanionConnectUiStateTest {
    @Test
    fun unknownNetworkUsesStableCheckingFeedbackAndDisablesScanning() {
        val result = CompanionUiState(network = CompanionNetworkState.Unknown).toConnectUiState()

        assertEquals(CompanionConnectFeedback.CheckingNetwork, result.feedback)
        assertEquals(CompanionConnectScanState.Disabled, result.scanState)
        assertEquals(CompanionConnectVisualMode.Illustration, result.visualMode)
    }

    @Test
    fun availableNetworkEnablesTheOnlyConnectAction() {
        val result = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
        ).toConnectUiState()

        assertEquals(CompanionConnectFeedback.SecureQrReady, result.feedback)
        assertEquals(CompanionConnectScanState.Enabled, result.scanState)
        assertEquals(CompanionConnectVisualMode.Illustration, result.visualMode)
    }

    @Test
    fun unavailableNetworkUsesARecoveryMessageWithoutEnablingScanning() {
        val result = CompanionUiState(network = CompanionNetworkState.Unavailable).toConnectUiState()

        assertEquals(CompanionConnectFeedback.NetworkUnavailable, result.feedback)
        assertEquals(CompanionConnectScanState.Disabled, result.scanState)
        assertEquals(CompanionConnectVisualMode.Illustration, result.visualMode)
    }

    @Test
    fun operationStateUsesLoadingWithoutChangingTheFeedbackContract() {
        val result = CompanionUiState(
            network = CompanionNetworkState.Available(metered = true),
            operationInProgress = true,
        ).toConnectUiState()

        assertEquals(CompanionConnectFeedback.SecureQrReady, result.feedback)
        assertEquals(CompanionConnectScanState.Loading, result.scanState)
        assertEquals(CompanionConnectVisualMode.Illustration, result.visualMode)
    }

    @Test
    fun activeScannerRemainsMountedWhileInvitationResolutionIsRunning() {
        val result = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
            operationInProgress = true,
        ).toConnectUiState()

        assertEquals(CompanionConnectVisualMode.Scanner, result.visualMode)
        assertEquals(CompanionConnectScanState.Loading, result.scanState)
        assertEquals(CompanionConnectFeedback.SecureQrReady, result.feedback)
    }

    @Test
    fun typedFailureTakesFeedbackPriorityWithoutCollapsingNetworkInteractionState() {
        val failure = CompanionFailure(
            code = CompanionFailureCode.UNKNOWN,
            message = "Unable to start the scanner.",
            recoverable = true,
        )
        val result = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            failure = failure,
        ).toConnectUiState()

        assertEquals(CompanionConnectScanState.Enabled, result.scanState)
        assertEquals(CompanionConnectFeedback.SecureQrReady, result.feedback)
        assertEquals(failure, result.failure?.failure)
        assertEquals(CompanionConnectFailureKind.GENERAL, result.failure?.kind)
        assertEquals(CompanionConnectVisualMode.Illustration, result.visualMode)
    }

    @Test
    fun activeScannerKeepsConnectStageAndOwnsItsFailurePresentation() {
        val result = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
            invitationScannerVisible = true,
            failure = CompanionFailure(
                code = CompanionFailureCode.INVITATION_INVALID,
                message = "The QR code is not a valid KNet invitation.",
                recoverable = true,
            ),
        ).toConnectUiState()

        assertEquals(CompanionConnectVisualMode.Scanner, result.visualMode)
        assertEquals(CompanionConnectFeedback.SecureQrReady, result.feedback)
        assertEquals(CompanionConnectFailureKind.INVALID_QR, result.failure?.kind)
        assertEquals(CompanionConnectScanState.Enabled, result.scanState)
    }

    @Test
    fun retrievalFailureIsPresentedAsAnUnreachableDesktop() {
        val result = stateWithFailure(CompanionFailureCode.INVITATION_RETRIEVAL_FAILED).toConnectUiState()

        assertEquals(CompanionConnectFailureKind.DESKTOP_UNREACHABLE, result.failure?.kind)
    }

    @Test
    fun identityMismatchIsPresentedAsASecurityFailure() {
        val result = stateWithFailure(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH).toConnectUiState()

        assertEquals(CompanionConnectFailureKind.SECURITY, result.failure?.kind)
    }

    @Test
    fun successfulConnectStateHasNoFailureCard() {
        val result = CompanionUiState(
            network = CompanionNetworkState.Available(metered = false),
        ).toConnectUiState()

        assertNull(result.failure)
    }

    private fun stateWithFailure(code: CompanionFailureCode): CompanionUiState = CompanionUiState(
        network = CompanionNetworkState.Available(metered = false),
        invitationScannerVisible = true,
        failure = CompanionFailure(code = code, message = "Safe failure details.", recoverable = true),
    )
}
