package com.devuloopers.knet.companion.model

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.PairingInvitation
import kotlin.jvm.JvmInline

/** Stable desktop installation identity independent from its current address. */
@JvmInline
public value class CompanionDesktopId(public val value: String) {
    init {
        require(value.isPortableIdentifier(maximumLength = 128)) {
            "CompanionDesktopId must be a safe 1 to 128 character value."
        }
    }
}

/** Opaque handle used to locate a credential in platform-protected storage. */
@JvmInline
public value class CompanionCredentialReference(public val value: String) {
    init {
        require(value.isPortableIdentifier(maximumLength = 512)) {
            "CompanionCredentialReference must be a safe 1 to 512 character value."
        }
    }
}

/** Lowercase SHA-256 fingerprint used for explicit trust checks. */
@JvmInline
public value class Sha256Fingerprint(public val value: String) {
    init {
        require(SHA_256.matches(value)) { "Fingerprint must be 64 lowercase hexadecimal characters." }
    }

    private companion object {
        val SHA_256: Regex = Regex("[0-9a-f]{64}")
    }
}

/** Reachable companion service endpoint; brackets are not stored around IPv6 host text. */
public data class CompanionServiceEndpoint(
    public val host: String,
    public val port: Int,
    public val secure: Boolean,
) {
    init {
        require(host.length in 1..255 && host == host.trim() && host.none(Char::isUnsafeEndpointCharacter)) {
            "Companion endpoint host is invalid."
        }
        require(port in 1..65_535) { "Companion endpoint port must be between 1 and 65535." }
    }
}

/** Transport selected below companion application workflows. */
public enum class CompanionTransportKind {
    DIRECT_LAN,
    RELAY,
}

/** Explicit behavior for traffic the current companion cannot inspect. */
public enum class UnsupportedTrafficPolicy {
    /** Reject the flow so clients may fall back to a supported protocol such as TCP instead of QUIC. */
    REJECT,

    /** Route the flow outside KNet and identify the session as only partially inspected. */
    BYPASS,
}

/** Requested device acquisition mode. */
public enum class CompanionInspectionMode {
    /** Capture supported device flows through the platform VPN API. */
    DEVICE_VPN,

    /** Forward only applications explicitly configured to use the companion local proxy. */
    LOCAL_PROXY,
}

/** Secret-bearing, short-lived invitation decoded only in memory. */
public data class CompanionPairingInvitation(
    public val protocolVersion: Int,
    public val desktopId: CompanionDesktopId,
    public val desktopDisplayName: String,
    public val pairing: PairingInvitation,
    public val controlEndpoint: CompanionServiceEndpoint,
    public val proxyEndpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val rootCertificateSha256: Sha256Fingerprint,
) {
    init {
        require(protocolVersion == CURRENT_PROTOCOL_VERSION) { "Unsupported companion protocol version." }
        require(desktopDisplayName.isNotBlank() && desktopDisplayName.length <= 128) {
            "Desktop display name must contain 1 to 128 characters."
        }
        require(desktopDisplayName.none(Char::isControlCharacter)) { "Desktop display name contains control characters." }
        require(controlEndpoint.secure) { "Companion control endpoint must be secure." }
        require(proxyEndpoint.secure) { "Companion proxy endpoint must be secure." }
    }

    public companion object {
        public const val CURRENT_PROTOCOL_VERSION: Int = 1
    }
}

/** Non-secret durable registration; credential material is referenced but never embedded. */
public data class CompanionRegistration(
    public val desktopId: CompanionDesktopId,
    public val desktopDisplayName: String,
    public val deviceId: RegisteredDeviceId,
    public val controlEndpoint: CompanionServiceEndpoint,
    public val proxyEndpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val rootCertificateSha256: Sha256Fingerprint,
    public val credentialReference: CompanionCredentialReference,
    public val scopes: Set<DeviceScope>,
    public val pairedAtEpochMillis: Long,
    public val credentialExpiresAtEpochMillis: Long,
) {
    init {
        require(desktopDisplayName.isNotBlank() && desktopDisplayName.length <= 128)
        require(desktopDisplayName.none(Char::isControlCharacter))
        require(controlEndpoint.secure && proxyEndpoint.secure) { "Companion registrations require secure endpoints." }
        require(scopes.isNotEmpty()) { "A companion registration must grant at least one scope." }
        require(pairedAtEpochMillis >= 0L)
        require(credentialExpiresAtEpochMillis > pairedAtEpochMillis)
    }
}

/** Public device identity with a platform-protected signing-key handle. */
public data class CompanionDeviceIdentity(
    public val deviceId: RegisteredDeviceId,
    public val proofAlgorithm: DeviceProofAlgorithm,
    public val publicKeyEncoded: String,
    public val privateKeyReference: String,
) {
    init {
        require(publicKeyEncoded.length in 1..16_384 && publicKeyEncoded.none(Char::isControlCharacter))
        require(privateKeyReference.isPortableIdentifier(maximumLength = 256))
    }
}

/** Current network reachability as observed by a platform adapter. */
public sealed interface CompanionNetworkState {
    public data object Unknown : CompanionNetworkState
    public data object Unavailable : CompanionNetworkState
    public data class Available(public val metered: Boolean) : CompanionNetworkState
}

/** Certificate-installation capability, separate from actual end-to-end trust verification. */
public sealed interface CompanionCertificateState {
    public data object Unknown : CompanionCertificateState
    public data object Missing : CompanionCertificateState
    public data object InstalledButUnverified : CompanionCertificateState
    public data class Trusted(public val verifiedAtEpochMillis: Long) : CompanionCertificateState
    public data class Rejected(public val reason: CompanionFailure) : CompanionCertificateState
}

/** Authenticated transport lifecycle shared by direct and future relay implementations. */
public sealed interface CompanionConnectionState {
    public data object Disconnected : CompanionConnectionState
    public data class Connecting(
        public val desktopId: CompanionDesktopId,
        public val attempt: Int,
    ) : CompanionConnectionState
    public data class Connected(
        public val desktopId: CompanionDesktopId,
        public val transport: CompanionTransportKind,
        public val connectedAtEpochMillis: Long,
    ) : CompanionConnectionState
    public data class Reconnecting(
        public val desktopId: CompanionDesktopId,
        public val attempt: Int,
    ) : CompanionConnectionState
    public data class Failed(public val failure: CompanionFailure) : CompanionConnectionState
}

/** Platform capture lifecycle. A connected transport does not imply that capture is active. */
public sealed interface CompanionInspectionState {
    public data object Stopped : CompanionInspectionState
    public data object Preparing : CompanionInspectionState
    public data object AwaitingVpnConsent : CompanionInspectionState
    public data class Running(
        public val mode: CompanionInspectionMode,
        public val startedAtEpochMillis: Long,
        public val fullHttpsInspection: Boolean,
    ) : CompanionInspectionState
    public data object Stopping : CompanionInspectionState
    public data class Failed(public val failure: CompanionFailure) : CompanionInspectionState
}

/** Stable failure categories; presentation never parses exception text. */
public enum class CompanionFailureCode {
    INVITATION_INVALID,
    INVITATION_EXPIRED,
    PAIRING_REJECTED,
    REGISTRATION_NOT_FOUND,
    CREDENTIAL_NOT_FOUND,
    CREDENTIAL_EXPIRED,
    NETWORK_UNAVAILABLE,
    TRANSPORT_UNAVAILABLE,
    TRANSPORT_IDENTITY_MISMATCH,
    CERTIFICATE_UNAVAILABLE,
    CERTIFICATE_NOT_TRUSTED,
    VPN_PERMISSION_DENIED,
    VPN_START_FAILED,
    PERSISTENCE_FAILED,
    CANCELLED,
    UNKNOWN,
}

/** Sanitized, platform-neutral failure suitable for durable state and UI. */
public data class CompanionFailure(
    public val code: CompanionFailureCode,
    public val message: String,
    public val recoverable: Boolean,
) {
    init {
        require(message.isNotBlank() && message.length <= 512)
        require(message.none(Char::isControlCharacter))
    }
}

private fun String.isPortableIdentifier(maximumLength: Int): Boolean =
    length in 1..maximumLength && isNotBlank() && this == trim() && none(Char::isControlCharacter)

private fun Char.isControlCharacter(): Boolean = code in 0..31 || code == 127

private fun Char.isUnsafeEndpointCharacter(): Boolean =
    isControlCharacter() || isWhitespace() || this in "/\\?#@[]%"
