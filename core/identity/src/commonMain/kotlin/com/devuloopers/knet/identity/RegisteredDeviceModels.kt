package com.devuloopers.knet.identity

/**
 * Stable non-secret device identity used across registration, pairing, ingress, traffic, and revocation.
 *
 * @property value Non-blank opaque identifier generated during first registration.
 */
@JvmInline
public value class RegisteredDeviceId(public val value: String) {
    init {
        require(value.isNotBlank()) { "RegisteredDeviceId must not be blank." }
    }
}

/** Describes the strongest enrollment mechanism currently associated with a registered device. */
public enum class DeviceRegistrationKind {
    /** A companion capable of proving possession of its private key and issued credential. */
    PAIRED_COMPANION,
}

/**
 * Durable user-visible device identity independent from any current network address or connection.
 *
 * Source IP and MAC observations are intentionally excluded because they are not stable authenticators.
 *
 * @property id Stable opaque identity shared by connectivity and pairing.
 * @property displayName User-visible name selected during registration.
 * @property registrationKind Strongest enrollment mechanism currently attached to the identity.
 * @property registeredAtEpochMillis Time at which KNet first registered the identity.
 * @property lastSeenAtEpochMillis Most recent successful identity association or authentication.
 * @property revokedAtEpochMillis Time at which the identity was revoked, or `null` while active.
 */
public data class RegisteredDevice(
    public val id: RegisteredDeviceId,
    public val displayName: String,
    public val registrationKind: DeviceRegistrationKind,
    public val registeredAtEpochMillis: Long,
    public val lastSeenAtEpochMillis: Long,
    public val revokedAtEpochMillis: Long? = null,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= 128)
        require(registeredAtEpochMillis >= 0L)
        require(lastSeenAtEpochMillis >= registeredAtEpochMillis)
        require(revokedAtEpochMillis == null || revokedAtEpochMillis >= registeredAtEpochMillis)
    }

    /** Whether this identity has been explicitly revoked and may no longer be selected or authenticated. */
    public val isRevoked: Boolean get() = revokedAtEpochMillis != null
}
