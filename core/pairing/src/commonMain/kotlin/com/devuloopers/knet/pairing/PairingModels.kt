package com.devuloopers.knet.pairing

import com.devuloopers.knet.identity.RegisteredDevice
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlin.jvm.JvmInline

@JvmInline
value class PairingInvitationId(val value: String) {
    init { require(value.isNotBlank()) }
}

/** Authorization is explicit and additive; credentials never imply every future capability. */
enum class DeviceScope {
    PROXY_STREAM,
    SETUP_ARTIFACT_READ,
    TRAFFIC_METADATA_READ,
}

/** Proof algorithm is transcript-bound so adding mobile-safe keys cannot create a downgrade ambiguity. */
enum class DeviceProofAlgorithm {
    ED25519,
    ECDSA_P256_SHA256,
}

/** One-time onboarding material returned only to the creator and never stored in plaintext. */
data class PairingInvitation(
    val id: PairingInvitationId,
    val secret: String,
    val expiresAtEpochMillis: Long,
    val scopes: Set<DeviceScope>,
) {
    init {
        require(secret.length in 16..512)
        require(expiresAtEpochMillis > 0L)
        require(scopes.isNotEmpty())
    }
}

/** Durable pending invitation with a one-way secret digest. */
data class PendingPairingInvitation(
    val id: PairingInvitationId,
    val secretDigest: String,
    val expiresAtEpochMillis: Long,
    val scopes: Set<DeviceScope>,
    val createdAtEpochMillis: Long,
)

/** Device request proving possession of its declared public key and one-time invitation secret. */
data class PairingCompletionRequest(
    val invitationId: PairingInvitationId,
    val invitationSecret: String,
    val deviceId: RegisteredDeviceId,
    val displayName: String,
    val publicKeyEncoded: String,
    val proofSignatureEncoded: String,
    val proofAlgorithm: DeviceProofAlgorithm = DeviceProofAlgorithm.ED25519,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= 128)
        require(publicKeyEncoded.isNotBlank() && proofSignatureEncoded.isNotBlank())
    }

    fun proofMessage(): String =
        "${proofAlgorithm.name}|${invitationId.value}|$invitationSecret|${deviceId.value}|$displayName"
}

/**
 * Persisted cryptographic trust attached to one durable registered identity.
 *
 * @property registeredDevice Canonical durable identity; its name and revocation state are authoritative.
 * @property publicKeyEncoded Encoded public key used to verify device proof.
 * @property credentialDigest One-way digest of the issued high-entropy credential.
 * @property scopes Capabilities granted to the credential.
 * @property pairedAtEpochMillis Time at which pairing completed.
 * @property credentialExpiresAtEpochMillis Time after which authentication must be rejected.
 */
data class TrustedDevice(
    val registeredDevice: RegisteredDevice,
    val publicKeyEncoded: String,
    val credentialDigest: String,
    val scopes: Set<DeviceScope>,
    val pairedAtEpochMillis: Long,
    val credentialExpiresAtEpochMillis: Long,
) {
    /** Stable registered identity used by ingress and revocation. */
    val id: RegisteredDeviceId get() = registeredDevice.id

    /** Current user-visible name from the registered-device source of truth. */
    val displayName: String get() = registeredDevice.displayName

    /** Revocation time shared with the registered identity. */
    val revokedAtEpochMillis: Long? get() = registeredDevice.revokedAtEpochMillis

    /** Whether the underlying registered identity has been revoked. */
    val isRevoked: Boolean get() = registeredDevice.isRevoked
}

/** Credential is returned once and is deliberately absent from [TrustedDevice]. */
data class IssuedDeviceCredential(
    val device: TrustedDevice,
    val credential: String,
)

/** Authenticated, scope-checked non-secret principal used by ingress adapters. */
data class PairedDevicePrincipal(
    val deviceId: RegisteredDeviceId,
    val displayName: String,
    val scopes: Set<DeviceScope>,
)

sealed interface PairingCompletionResult {
    data class Paired(val issued: IssuedDeviceCredential) : PairingCompletionResult
    data class Rejected(val code: String) : PairingCompletionResult
}

/** Result of atomically replacing one valid paired-device credential. */
sealed interface PairingCredentialRefreshResult {
    /** Newly issued credential and updated durable device state. */
    data class Refreshed(val issued: IssuedDeviceCredential) : PairingCredentialRefreshResult

    /** Non-secret stable reason the credential could not be rotated. */
    data class Rejected(val code: String) : PairingCredentialRefreshResult
}

sealed interface DeviceAuthenticationResult {
    data class Authenticated(val principal: PairedDevicePrincipal) : DeviceAuthenticationResult
    data class Rejected(val code: String) : DeviceAuthenticationResult
}
