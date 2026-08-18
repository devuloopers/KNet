package com.devuloopers.knet.application.port.connectivity.wifi

import com.devuloopers.knet.connectivity.model.WifiApprovedClient
import com.devuloopers.knet.connectivity.model.WifiClientCandidateId
import com.devuloopers.knet.connectivity.model.WifiClientId
import com.devuloopers.knet.connectivity.model.WifiInvitation
import com.devuloopers.knet.connectivity.model.WifiSharingConfiguration
import com.devuloopers.knet.connectivity.model.WifiSharingState
import kotlinx.coroutines.flow.StateFlow

/** Reason supplied when application orchestration disables Wi-Fi sharing. */
public enum class WifiSharingStopReason {
    USER_REQUEST,
    APPLICATION_SHUTDOWN,
    NETWORK_CHANGED,
    PROXY_STOPPED,
}

/** Result of enabling or disabling the Wi-Fi sharing runtime. */
public sealed interface WifiSharingOperationResult {
    /** The requested lifecycle state has been reached. */
    public data object Succeeded : WifiSharingOperationResult

    /** The operation was rejected or rolled back with a safe stable code. */
    public data class Rejected(public val code: String) : WifiSharingOperationResult {
        init {
            require(code.isNotBlank()) { "Wi-Fi rejection code must not be blank." }
        }
    }
}

/** Result of creating a new short-lived phone invitation. */
public sealed interface WifiInvitationResult {
    /** Invitation created for the active Wi-Fi session. */
    public data class Created(public val invitation: WifiInvitation) : WifiInvitationResult

    /** No invitation was created. */
    public data class Rejected(public val code: String) : WifiInvitationResult {
        init {
            require(code.isNotBlank()) { "Wi-Fi invitation rejection code must not be blank." }
        }
    }
}

/** Result of approving one phone observed by the setup endpoint. */
public sealed interface WifiClientApprovalResult {
    /** Candidate is now authorized for the current sharing session. */
    public data class Approved(public val client: WifiApprovedClient) : WifiClientApprovalResult

    /** Candidate could not be approved. */
    public data class Rejected(public val code: String) : WifiClientApprovalResult {
        init {
            require(code.isNotBlank()) { "Wi-Fi approval rejection code must not be blank." }
        }
    }
}

/**
 * Application boundary for stock-phone Wi-Fi connectivity.
 *
 * Implementations own LAN listeners and approval state but never proxy parsing, capture, persistence,
 * body access, or semantic inspection.
 */
public interface WifiSharingPort {
    /** Current serialized Wi-Fi lifecycle and presentation-safe client state. */
    public val state: StateFlow<WifiSharingState>

    /** Atomically binds the exact-interface gateway and setup endpoint. */
    public suspend fun enable(configuration: WifiSharingConfiguration): WifiSharingOperationResult

    /** Closes listeners, active sockets, invitations, and session-bound approvals. */
    public suspend fun disable(reason: WifiSharingStopReason): WifiSharingOperationResult

    /** Creates a new short-lived onboarding invitation for the active session. */
    public suspend fun createInvitation(): WifiInvitationResult

    /** Approves a pending source address for the active session. */
    public suspend fun approve(
        candidateId: WifiClientCandidateId,
        displayName: String,
    ): WifiClientApprovalResult

    /** Rejects a pending source address without authorizing it. */
    public suspend fun reject(candidateId: WifiClientCandidateId): WifiSharingOperationResult

    /** Revokes one approved phone and closes all of its active gateway sockets. */
    public suspend fun revoke(clientId: WifiClientId): WifiSharingOperationResult
}
