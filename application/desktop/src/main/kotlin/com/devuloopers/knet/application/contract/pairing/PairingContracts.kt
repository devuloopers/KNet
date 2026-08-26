package com.devuloopers.knet.application.contract.pairing

import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.pairing.TrustedDevice
import kotlinx.coroutines.flow.Flow

/** Cryptographic operations supplied by a platform adapter. */
public interface PairingCryptography {
    public fun randomToken(entropyBytes: Int): String
    public fun digest(value: String): String
    public fun constantTimeMatches(value: String, expectedDigest: String): Boolean
    public fun verifyDeviceProof(
        algorithm: DeviceProofAlgorithm,
        publicKeyEncoded: String,
        message: String,
        signatureEncoded: String,
    ): Boolean
}

/**
 * Durable registry for every user-recognized device, including stock Wi-Fi phones and paired companions.
 *
 * Implementations persist identity only. Current source-address authorization remains owned by the active
 * connectivity runtime and must never be restored from this registry.
 */
public interface RegisteredDeviceStore {
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
public interface TrustedDeviceStore {
    public suspend fun putInvitation(invitation: PendingPairingInvitation)
    public suspend fun claimInvitation(
        id: PairingInvitationId,
        secretDigest: String,
        nowEpochMillis: Long,
    ): PendingPairingInvitation?
    public suspend fun putDevice(device: TrustedDevice)
    public suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice?
    /**
     * Replaces one credential digest only when [expectedCredentialDigest] is still current and the identity remains
     * active, preventing concurrent refresh requests from issuing multiple usable credentials.
     */
    public suspend fun rotateCredential(
        id: RegisteredDeviceId,
        expectedCredentialDigest: String,
        newCredentialDigest: String,
        credentialExpiresAtEpochMillis: Long,
    ): Boolean
    public suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean
    public fun observeDevices(): Flow<List<TrustedDevice>>
}
