package com.devuloopers.knet.application.coordinator.pairing

import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.pairing.DeviceAuthenticationResult
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingCompletionResult
import com.devuloopers.knet.pairing.PairingCredentialRefreshResult
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.TrustedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PairingCoordinatorTest {
    @Test
    fun `invitation is one-shot scoped expiring and revocable`() = runTest {
        var now = 1_000L
        val store = FakeStore()
        val coordinator = PairingCoordinator(store, FakeCrypto(), { now }, 10_000L, 60_000L)
        val invitation = coordinator.createInvitation(setOf(DeviceScope.PROXY_STREAM))
        val request = PairingCompletionRequest(
            invitation.id,
            invitation.secret,
            RegisteredDeviceId("device-1"),
            "Test device",
            "public-key",
            "valid-proof",
        )

        val paired = assertIs<PairingCompletionResult.Paired>(coordinator.complete(request))
        assertIs<PairingCompletionResult.Rejected>(coordinator.complete(request))
        assertIs<DeviceAuthenticationResult.Authenticated>(
            coordinator.authenticate(paired.issued.device.id, paired.issued.credential, DeviceScope.PROXY_STREAM),
        )
        assertIs<DeviceAuthenticationResult.Rejected>(
            coordinator.authenticate(paired.issued.device.id, paired.issued.credential, DeviceScope.TRAFFIC_METADATA_READ),
        )
        val refreshed = assertIs<PairingCredentialRefreshResult.Refreshed>(
            coordinator.refreshCredential(paired.issued.device.id, paired.issued.credential),
        )
        assertIs<DeviceAuthenticationResult.Rejected>(
            coordinator.authenticate(paired.issued.device.id, paired.issued.credential, DeviceScope.PROXY_STREAM),
        )
        assertIs<DeviceAuthenticationResult.Authenticated>(
            coordinator.authenticate(refreshed.issued.device.id, refreshed.issued.credential, DeviceScope.PROXY_STREAM),
        )
        assertIs<PairingCredentialRefreshResult.Rejected>(
            coordinator.refreshCredential(paired.issued.device.id, paired.issued.credential),
        )
        assertTrue(coordinator.revoke(paired.issued.device.id))
        assertIs<DeviceAuthenticationResult.Rejected>(
            coordinator.authenticate(refreshed.issued.device.id, refreshed.issued.credential, DeviceScope.PROXY_STREAM),
        )

        val expired = coordinator.createInvitation(setOf(DeviceScope.PROXY_STREAM))
        now = expired.expiresAtEpochMillis
        assertIs<PairingCompletionResult.Rejected>(
            coordinator.complete(request.copy(invitationId = expired.id, invitationSecret = expired.secret)),
        )
    }

    private class FakeCrypto : PairingCryptography {
        private var token = 0
        override fun randomToken(entropyBytes: Int): String = "token-${++token}-0123456789abcdef"
        override fun digest(value: String): String = "digest:$value"
        override fun constantTimeMatches(value: String, expectedDigest: String): Boolean = digest(value) == expectedDigest
        override fun verifyDeviceProof(
            algorithm: DeviceProofAlgorithm,
            publicKeyEncoded: String,
            message: String,
            signatureEncoded: String,
        ): Boolean =
            signatureEncoded == "valid-proof"
    }

    private class FakeStore : TrustedDeviceStore {
        private val invitations = mutableMapOf<PairingInvitationId, PendingPairingInvitation>()
        private val devices = mutableMapOf<RegisteredDeviceId, TrustedDevice>()
        private val flow = MutableStateFlow<List<TrustedDevice>>(emptyList())
        override suspend fun putInvitation(invitation: PendingPairingInvitation) { invitations[invitation.id] = invitation }
        override suspend fun claimInvitation(
            id: PairingInvitationId,
            secretDigest: String,
            nowEpochMillis: Long,
        ): PendingPairingInvitation? {
            val invitation = invitations[id] ?: return null
            if (invitation.secretDigest != secretDigest || nowEpochMillis >= invitation.expiresAtEpochMillis) return null
            invitations.remove(id)
            return invitation
        }
        override suspend fun putDevice(device: TrustedDevice) { devices[device.id] = device; flow.value = devices.values.toList() }
        override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? = devices[id]
        override suspend fun rotateCredential(
            id: RegisteredDeviceId,
            expectedCredentialDigest: String,
            newCredentialDigest: String,
            credentialExpiresAtEpochMillis: Long,
        ): Boolean {
            val device = devices[id] ?: return false
            if (device.isRevoked || device.credentialDigest != expectedCredentialDigest) return false
            devices[id] = device.copy(
                credentialDigest = newCredentialDigest,
                credentialExpiresAtEpochMillis = credentialExpiresAtEpochMillis,
            )
            flow.value = devices.values.toList()
            return true
        }
        override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean {
            val device = devices[id] ?: return false
            devices[id] = device.copy(
                registeredDevice = device.registeredDevice.copy(revokedAtEpochMillis = revokedAtEpochMillis),
            )
            flow.value = devices.values.toList()
            return true
        }
        override fun observeDevices(): Flow<List<TrustedDevice>> = flow
    }
}
