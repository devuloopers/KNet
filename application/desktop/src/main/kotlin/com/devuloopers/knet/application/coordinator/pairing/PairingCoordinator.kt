package com.devuloopers.knet.application.coordinator.pairing

import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.identity.DeviceRegistrationKind
import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceAuthenticationResult
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.IssuedDeviceCredential
import com.devuloopers.knet.pairing.PairedDevicePrincipal
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingCompletionResult
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.pairing.TrustedDevice
import kotlinx.coroutines.flow.Flow

/** Application pairing state machine with expiry, one-shot replay defense, proof, scope, and revocation. */
public class PairingCoordinator(
    private val store: TrustedDeviceStore,
    private val crypto: PairingCryptography,
    private val nowMillis: () -> Long,
    private val invitationLifetimeMillis: Long = 5L * 60L * 1_000L,
    private val credentialLifetimeMillis: Long = 90L * 24L * 60L * 60L * 1_000L,
) {
    init {
        require(invitationLifetimeMillis in 10_000L..(24L * 60L * 60L * 1_000L))
        require(credentialLifetimeMillis in 60_000L..(366L * 24L * 60L * 60L * 1_000L))
    }

    public suspend fun createInvitation(scopes: Set<DeviceScope>): PairingInvitation {
        require(scopes.isNotEmpty())
        val now = nowMillis()
        val id = PairingInvitationId(crypto.randomToken(18))
        val secret = crypto.randomToken(32)
        val invitation = PairingInvitation(id, secret, now + invitationLifetimeMillis, scopes)
        store.putInvitation(
            PendingPairingInvitation(id, crypto.digest(secret), invitation.expiresAtEpochMillis, scopes, now),
        )
        return invitation
    }

    public suspend fun complete(request: PairingCompletionRequest): PairingCompletionResult {
        val now = nowMillis()
        if (!crypto.verifyDeviceProof(
                request.proofAlgorithm,
                request.publicKeyEncoded,
                request.proofMessage(),
                request.proofSignatureEncoded,
            )
        ) return PairingCompletionResult.Rejected("device_proof_invalid")
        val claimed = store.claimInvitation(
            request.invitationId,
            crypto.digest(request.invitationSecret),
            now,
        ) ?: return PairingCompletionResult.Rejected("invitation_invalid_expired_or_replayed")
        val credential = crypto.randomToken(48)
        val device = TrustedDevice(
            registeredDevice = RegisteredDevice(
                id = request.deviceId,
                displayName = request.displayName,
                registrationKind = DeviceRegistrationKind.PAIRED_COMPANION,
                registeredAtEpochMillis = now,
                lastSeenAtEpochMillis = now,
            ),
            publicKeyEncoded = request.publicKeyEncoded,
            credentialDigest = crypto.digest(credential),
            scopes = claimed.scopes,
            pairedAtEpochMillis = now,
            credentialExpiresAtEpochMillis = now + credentialLifetimeMillis,
        )
        store.putDevice(device)
        return PairingCompletionResult.Paired(IssuedDeviceCredential(device, credential))
    }

    public suspend fun authenticate(
        deviceId: RegisteredDeviceId,
        credential: String,
        requiredScope: DeviceScope,
    ): DeviceAuthenticationResult {
        val device = store.getDevice(deviceId)
            ?: return DeviceAuthenticationResult.Rejected("device_unknown")
        val now = nowMillis()
        if (device.isRevoked) return DeviceAuthenticationResult.Rejected("device_revoked")
        if (now >= device.credentialExpiresAtEpochMillis) return DeviceAuthenticationResult.Rejected("credential_expired")
        if (requiredScope !in device.scopes) return DeviceAuthenticationResult.Rejected("scope_denied")
        if (!crypto.constantTimeMatches(credential, device.credentialDigest)) {
            return DeviceAuthenticationResult.Rejected("credential_invalid")
        }
        return DeviceAuthenticationResult.Authenticated(
            PairedDevicePrincipal(device.id, device.displayName, device.scopes),
        )
    }

    public suspend fun revoke(deviceId: RegisteredDeviceId): Boolean = store.revoke(deviceId, nowMillis())

    public fun observeDevices(): Flow<List<TrustedDevice>> = store.observeDevices()
}
