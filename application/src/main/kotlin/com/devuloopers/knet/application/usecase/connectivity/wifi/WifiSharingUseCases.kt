package com.devuloopers.knet.application.usecase.connectivity.wifi

import com.devuloopers.knet.application.port.connectivity.wifi.WifiClientApprovalResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiInvitationResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingOperationResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingPort
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingStopReason
import com.devuloopers.knet.connectivity.model.WifiClientCandidateId
import com.devuloopers.knet.connectivity.model.WifiClientId
import com.devuloopers.knet.connectivity.model.WifiSharingConfiguration
import com.devuloopers.knet.connectivity.model.WifiSharingState
import kotlinx.coroutines.flow.StateFlow

/** Enables the primary stock-phone Wi-Fi connection path. */
public class EnableWifiSharingUseCase(private val sharing: WifiSharingPort) {
    public suspend fun execute(configuration: WifiSharingConfiguration): WifiSharingOperationResult =
        sharing.enable(configuration)
}

/** Disables Wi-Fi sharing without stopping the loopback proxy or clearing captured traffic. */
public class DisableWifiSharingUseCase(private val sharing: WifiSharingPort) {
    public suspend fun execute(
        reason: WifiSharingStopReason = WifiSharingStopReason.USER_REQUEST,
    ): WifiSharingOperationResult = sharing.disable(reason)
}

/** Observes the serialized Wi-Fi sharing state. */
public class ObserveWifiSharingUseCase(private val sharing: WifiSharingPort) {
    public fun execute(): StateFlow<WifiSharingState> = sharing.state
}

/** Creates another short-lived onboarding invitation for the active sharing session. */
public class CreateWifiInvitationUseCase(private val sharing: WifiSharingPort) {
    public suspend fun execute(): WifiInvitationResult = sharing.createInvitation()
}

/** Approves one pending phone for the active sharing session. */
public class ApproveWifiClientUseCase(private val sharing: WifiSharingPort) {
    public suspend fun execute(
        candidateId: WifiClientCandidateId,
        displayName: String,
    ): WifiClientApprovalResult = sharing.approve(candidateId, displayName)
}

/** Rejects one pending phone. */
public class RejectWifiClientUseCase(private val sharing: WifiSharingPort) {
    public suspend fun execute(candidateId: WifiClientCandidateId): WifiSharingOperationResult =
        sharing.reject(candidateId)
}

/** Revokes one approved phone and terminates its active gateway streams. */
public class RevokeWifiClientUseCase(private val sharing: WifiSharingPort) {
    public suspend fun execute(clientId: WifiClientId): WifiSharingOperationResult = sharing.revoke(clientId)
}
