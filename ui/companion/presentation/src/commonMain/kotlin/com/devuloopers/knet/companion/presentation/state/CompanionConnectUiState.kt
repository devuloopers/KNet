package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionNetworkState

/** Stable feedback occupying the Connect screen's reserved status region. */
public sealed interface CompanionConnectFeedback {
    /** The local network observer has not produced an authoritative result yet. */
    public data object CheckingNetwork : CompanionConnectFeedback

    /** The device can start the QR-only connection flow. */
    public data object SecureQrReady : CompanionConnectFeedback

    /** Pairing cannot proceed until local network connectivity returns. */
    public data object NetworkUnavailable : CompanionConnectFeedback

}

/** Semantic failure treatment for the inline Connect recovery card. */
public enum class CompanionConnectFailureKind {
    INVALID_QR,
    EXPIRED_QR,
    DESKTOP_UNREACHABLE,
    SECURITY,
    PAIRING,
    GENERAL,
}

/** Presentation-safe failure and its semantic Connect treatment. */
public data class CompanionConnectFailureUiState(
    public val failure: CompanionFailure,
    public val kind: CompanionConnectFailureKind,
)

/** Explicit interaction state for the single Connect-screen action. */
public sealed interface CompanionConnectScanState {
    public data object Enabled : CompanionConnectScanState
    public data object Disabled : CompanionConnectScanState
    public data object Loading : CompanionConnectScanState
}

/** Stable content occupying the Connect card's visual panel. */
public enum class CompanionConnectVisualMode {
    Illustration,
    Scanner,
}

/** Complete immutable render contract for the QR-only Connect screen. */
public data class CompanionConnectUiState(
    public val feedback: CompanionConnectFeedback,
    public val failure: CompanionConnectFailureUiState?,
    public val scanState: CompanionConnectScanState,
    public val visualMode: CompanionConnectVisualMode,
)

/** Derives a deterministic Connect render model without retaining transient UI-owned state. */
public fun CompanionUiState.toConnectUiState(): CompanionConnectUiState {
    val visualMode = if (invitationScannerVisible) {
        CompanionConnectVisualMode.Scanner
    } else {
        CompanionConnectVisualMode.Illustration
    }
    val feedback = when {
        network == CompanionNetworkState.Unknown -> CompanionConnectFeedback.CheckingNetwork
        network == CompanionNetworkState.Unavailable -> CompanionConnectFeedback.NetworkUnavailable
        else -> CompanionConnectFeedback.SecureQrReady
    }
    val scanState = when {
        operationInProgress -> CompanionConnectScanState.Loading
        network is CompanionNetworkState.Available -> CompanionConnectScanState.Enabled
        else -> CompanionConnectScanState.Disabled
    }
    return CompanionConnectUiState(
        feedback = feedback,
        failure = failure?.let { currentFailure ->
            CompanionConnectFailureUiState(
                failure = currentFailure,
                kind = currentFailure.code.toConnectFailureKind(),
            )
        },
        scanState = scanState,
        visualMode = visualMode,
    )
}

private fun CompanionFailureCode.toConnectFailureKind(): CompanionConnectFailureKind =
    when (this) {
        CompanionFailureCode.INVITATION_INVALID -> CompanionConnectFailureKind.INVALID_QR
        CompanionFailureCode.INVITATION_EXPIRED -> CompanionConnectFailureKind.EXPIRED_QR
        CompanionFailureCode.INVITATION_RETRIEVAL_FAILED,
        CompanionFailureCode.NETWORK_UNAVAILABLE,
        CompanionFailureCode.TRANSPORT_UNAVAILABLE,
        -> CompanionConnectFailureKind.DESKTOP_UNREACHABLE
        CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
        CompanionFailureCode.DESKTOP_IDENTITY_CONFLICT,
        -> CompanionConnectFailureKind.SECURITY
        CompanionFailureCode.PAIRING_REJECTED -> CompanionConnectFailureKind.PAIRING
        else -> CompanionConnectFailureKind.GENERAL
    }
