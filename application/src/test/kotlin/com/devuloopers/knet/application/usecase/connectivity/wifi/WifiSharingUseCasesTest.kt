package com.devuloopers.knet.application.usecase.connectivity.wifi

import com.devuloopers.knet.application.port.connectivity.wifi.WifiClientApprovalResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiInvitationResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingOperationResult
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingPort
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingStopReason
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.WifiClientCandidateId
import com.devuloopers.knet.connectivity.model.WifiClientId
import com.devuloopers.knet.connectivity.model.WifiInvitation
import com.devuloopers.knet.connectivity.model.WifiInvitationId
import com.devuloopers.knet.connectivity.model.WifiSharingConfiguration
import com.devuloopers.knet.connectivity.model.WifiSharingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WifiSharingUseCasesTest {
    @Test
    fun `use cases preserve typed Wi-Fi commands and results`() = runTest {
        val port = RecordingWifiSharingPort()
        val configuration = WifiSharingConfiguration(
            networkAddress = NetworkAddress("en0", "192.0.2.10", NetworkAddressFamily.IPV4, loopback = false),
            proxyPort = 8_080,
            setupPort = 8_181,
        )

        assertIs<WifiSharingOperationResult.Succeeded>(EnableWifiSharingUseCase(port).execute(configuration))
        assertEquals(configuration, port.enabledConfiguration)

        val invitation = assertIs<WifiInvitationResult.Created>(CreateWifiInvitationUseCase(port).execute())
        assertEquals("http://192.0.2.10:8181/invite/token", invitation.invitation.setupUrl)

        val candidateId = WifiClientCandidateId("candidate-1")
        assertIs<WifiClientApprovalResult.Rejected>(
            ApproveWifiClientUseCase(port).execute(candidateId, "Phone"),
        )
        assertEquals(candidateId, port.approvedCandidateId)
        assertEquals("Phone", port.approvedDisplayName)

        val clientId = WifiClientId("client-1")
        assertIs<WifiSharingOperationResult.Succeeded>(RevokeWifiClientUseCase(port).execute(clientId))
        assertEquals(clientId, port.revokedClientId)

        assertIs<WifiSharingOperationResult.Succeeded>(
            DisableWifiSharingUseCase(port).execute(WifiSharingStopReason.NETWORK_CHANGED),
        )
        assertEquals(WifiSharingStopReason.NETWORK_CHANGED, port.stopReason)
        assertEquals(port.state, ObserveWifiSharingUseCase(port).execute())
    }

    private class RecordingWifiSharingPort : WifiSharingPort {
        private val mutableState = MutableStateFlow<WifiSharingState>(WifiSharingState.Disabled(emptyList()))
        override val state: StateFlow<WifiSharingState> = mutableState
        var enabledConfiguration: WifiSharingConfiguration? = null
        var stopReason: WifiSharingStopReason? = null
        var approvedCandidateId: WifiClientCandidateId? = null
        var approvedDisplayName: String? = null
        var revokedClientId: WifiClientId? = null

        override suspend fun enable(configuration: WifiSharingConfiguration): WifiSharingOperationResult {
            enabledConfiguration = configuration
            return WifiSharingOperationResult.Succeeded
        }

        override suspend fun disable(reason: WifiSharingStopReason): WifiSharingOperationResult {
            stopReason = reason
            return WifiSharingOperationResult.Succeeded
        }

        override suspend fun createInvitation(): WifiInvitationResult = WifiInvitationResult.Created(
            WifiInvitation(
                id = WifiInvitationId("invitation-1"),
                setupUrl = "http://192.0.2.10:8181/invite/token",
                expiresAtEpochMillis = 10_000L,
            ),
        )

        override suspend fun approve(
            candidateId: WifiClientCandidateId,
            displayName: String,
        ): WifiClientApprovalResult {
            approvedCandidateId = candidateId
            approvedDisplayName = displayName
            return WifiClientApprovalResult.Rejected("not-implemented-in-fake")
        }

        override suspend fun reject(candidateId: WifiClientCandidateId): WifiSharingOperationResult =
            WifiSharingOperationResult.Succeeded

        override suspend fun revoke(clientId: WifiClientId): WifiSharingOperationResult {
            revokedClientId = clientId
            return WifiSharingOperationResult.Succeeded
        }
    }
}
