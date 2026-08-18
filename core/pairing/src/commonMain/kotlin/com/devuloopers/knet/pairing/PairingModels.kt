package com.devuloopers.knet.pairing

/** Stable non-secret device identity used across pairing, ingress, traffic, and revocation. */
@JvmInline
value class PairedDeviceId(val value: String) {
    init { require(value.isNotBlank()) }
}

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
    val deviceId: PairedDeviceId,
    val displayName: String,
    val publicKeyEncoded: String,
    val proofSignatureEncoded: String,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= 128)
        require(publicKeyEncoded.isNotBlank() && proofSignatureEncoded.isNotBlank())
    }

    fun proofMessage(): String =
        "${invitationId.value}|$invitationSecret|${deviceId.value}|$displayName"
}

/** Persisted trusted device; only the credential digest is retained. */
data class TrustedDevice(
    val id: PairedDeviceId,
    val displayName: String,
    val publicKeyEncoded: String,
    val credentialDigest: String,
    val scopes: Set<DeviceScope>,
    val pairedAtEpochMillis: Long,
    val credentialExpiresAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
) {
    val isRevoked: Boolean get() = revokedAtEpochMillis != null
}

/** Credential is returned once and is deliberately absent from [TrustedDevice]. */
data class IssuedDeviceCredential(
    val device: TrustedDevice,
    val credential: String,
)

/** Authenticated, scope-checked non-secret principal used by ingress adapters. */
data class PairedDevicePrincipal(
    val deviceId: PairedDeviceId,
    val displayName: String,
    val scopes: Set<DeviceScope>,
)

sealed interface PairingCompletionResult {
    data class Paired(val issued: IssuedDeviceCredential) : PairingCompletionResult
    data class Rejected(val code: String) : PairingCompletionResult
}

sealed interface DeviceAuthenticationResult {
    data class Authenticated(val principal: PairedDevicePrincipal) : DeviceAuthenticationResult
    data class Rejected(val code: String) : DeviceAuthenticationResult
}
