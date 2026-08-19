package com.devuloopers.knet.application.port.pairing

import com.devuloopers.knet.pairing.DeviceAuthenticationResult
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.IssuedDeviceCredential
import com.devuloopers.knet.pairing.PairedDevicePrincipal
import com.devuloopers.knet.pairing.PairingCompletionRequest
import com.devuloopers.knet.pairing.PairingCompletionResult
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.identity.DeviceRegistrationKind
import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.TrustedDevice
import kotlinx.coroutines.flow.Flow

/** Cryptographic operations supplied by a platform adapter. */
public interface PairingCryptoPort {
    public fun randomToken(entropyBytes: Int): String
    public fun digest(value: String): String
    public fun constantTimeMatches(value: String, expectedDigest: String): Boolean
    public fun verifyDeviceProof(publicKeyEncoded: String, message: String, signatureEncoded: String): Boolean
}

/**
 * Durable registry for every user-recognized device, including stock Wi-Fi phones and paired companions.
 *
 * Implementations persist identity only. Current source-address authorization remains owned by the active
 * connectivity runtime and must never be restored from this registry.
 */
public interface RegisteredDeviceStorePort {
    /** Inserts or replaces one durable registered-device identity. */
    public suspend fun putRegisteredDevice(device: RegisteredDevice)

    /** Returns one registered identity, including a revoked identity, when present. */
    public suspend fun getRegisteredDevice(id: RegisteredDeviceId): RegisteredDevice?

    /** Updates last-seen time without changing identity or authorization state. */
    public suspend fun markRegisteredDeviceSeen(id: RegisteredDeviceId, seenAtEpochMillis: Long): Boolean

    /** Revokes the durable identity without authorizing or disconnecting any runtime source address. */
    public suspend fun revokeRegisteredDevice(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean

    /** Observes active registered identities for presentation and explicit session association. */
    public fun observeRegisteredDevices(): Flow<List<RegisteredDevice>>
}

/** Atomic trusted-device storage. Plain invitation secrets and credentials are never accepted. */
public interface TrustedDeviceStorePort {
    public suspend fun putInvitation(invitation: PendingPairingInvitation)
    public suspend fun claimInvitation(
        id: PairingInvitationId,
        secretDigest: String,
        nowEpochMillis: Long,
    ): PendingPairingInvitation?
    public suspend fun putDevice(device: TrustedDevice)
    public suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice?
    public suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean
    public fun observeDevices(): Flow<List<TrustedDevice>>
}

/** Application pairing state machine with expiry, one-shot replay defense, proof, scope, and revocation. */
public class PairingCoordinator(
    private val store: TrustedDeviceStorePort,
    private val crypto: PairingCryptoPort,
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
