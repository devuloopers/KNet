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

/**
 * Immutable DER-encoded public root certificate carried by a companion pairing contract.
 *
 * @param bytes bounded public certificate material copied at construction.
 */
public class CompanionRootCertificate(bytes: ByteArray) {
    private val content: ByteArray = bytes.copyOf()

    init {
        require(content.size in 1..CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES) {
            "Companion root certificate exceeds the supported size."
        }
    }

    /** Returns a defensive copy of the DER-encoded certificate bytes. */
    public fun copyBytes(): ByteArray = content.copyOf()

    override fun equals(other: Any?): Boolean =
        other is CompanionRootCertificate && content.contentEquals(other.content)

    override fun hashCode(): Int = content.contentHashCode()
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

/** Opaque identifier for one short-lived companion invitation retrieval record. */
@JvmInline
public value class CompanionBootstrapId(public val value: String) {
    init {
        require(value.isPortableIdentifier(maximumLength = 128)) {
            "CompanionBootstrapId must be a safe 1 to 128 character value."
        }
    }
}

/** One-time secret used only to retrieve the complete pairing invitation. */
@JvmInline
public value class CompanionBootstrapSecret(public val value: String) {
    init {
        require(value.length in 16..512 && value.isPortableIdentifier(maximumLength = 512)) {
            "CompanionBootstrapSecret must be a safe 16 to 512 character value."
        }
    }
}

/**
 * Small secret-bearing bootstrap reference intended for QR and deep-link transport.
 *
 * The complete certificate and pairing configuration are deliberately absent. A client first downloads the
 * public KNet root from [rootCertificateEndpoint], authenticates it with [rootCertificateSha256], and then uses
 * platform PKIX trust to redeem [retrievalSecret] at [retrievalEndpoint]. [transportIdentitySha256] remains an
 * additional exact peer-chain identity check after TLS negotiation.
 *
 * @property protocolVersion wire version understood by both products.
 * @property id opaque one-time desktop record identifier.
 * @property retrievalSecret one-time credential sent only inside the pinned TLS request body.
 * @property expiresAtEpochMillis absolute expiry shared with the complete invitation.
 * @property rootCertificateEndpoint open LAN endpoint serving only the public KNet root certificate.
 * @property retrievalEndpoint secure endpoint that atomically consumes this bootstrap.
 * @property transportIdentitySha256 expected identity in the redemption server certificate chain.
 * @property rootCertificateSha256 exact fingerprint of the public root downloaded before redemption.
 */
public data class CompanionPairingBootstrap(
    public val protocolVersion: Int,
    public val id: CompanionBootstrapId,
    public val retrievalSecret: CompanionBootstrapSecret,
    public val expiresAtEpochMillis: Long,
    public val rootCertificateEndpoint: CompanionServiceEndpoint,
    public val retrievalEndpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val rootCertificateSha256: Sha256Fingerprint,
) {
    init {
        require(protocolVersion == CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION) {
            "Unsupported companion bootstrap protocol version."
        }
        require(expiresAtEpochMillis > 0L) { "Companion bootstrap expiry must be positive." }
        require(!rootCertificateEndpoint.secure) { "Companion bootstrap root endpoint must use open HTTP." }
        require(retrievalEndpoint.secure) { "Companion bootstrap retrieval endpoint must be secure." }
    }
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
    public val rootCertificate: CompanionRootCertificate,
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
        /** Current companion invitation and registration protocol version. */
        public const val CURRENT_PROTOCOL_VERSION: Int = 3
    }
}

/** Shared transport constants for lightweight invitation retrieval. */
public object CompanionBootstrapProtocol {
    /** Open HTTP path serving the public root whose fingerprint is carried by the bootstrap. */
    public const val ROOT_CERTIFICATE_PATH: String = "/knet-ca.crt"

    /** Expected media type for the public DER-encoded KNet root certificate. */
    public const val ROOT_CERTIFICATE_MEDIA_TYPE: String = "application/x-x509-ca-cert"

    /** Unauthenticated, TLS-pinned POST endpoint that consumes one retrieval secret. */
    public const val REDEEM_PATH: String = "/companion/v3/invitations/redeem"

    /** Media type for a bounded bootstrap redemption request body. */
    public const val REQUEST_MEDIA_TYPE: String = "application/vnd.knet.companion-bootstrap-request"

    /** Media type for a complete pairing invitation response body. */
    public const val RESPONSE_MEDIA_TYPE: String = "application/vnd.knet.companion-invitation"

    /** Maximum accepted bootstrap request body size. */
    public const val MAXIMUM_REQUEST_BYTES: Int = 2 * 1024

    /** Maximum accepted complete invitation response body size. */
    public const val MAXIMUM_RESPONSE_BYTES: Int = 32 * 1024
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
    public val rootCertificate: CompanionRootCertificate,
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
    /** Trust has not been checked for the active desktop registration. */
    public data object Unknown : CompanionCertificateState

    /** The expected KNet root did not complete platform trust validation and must be installed or enabled. */
    public data object InstallationRequired : CompanionCertificateState

    /** A bounded end-to-end challenge is currently checking the installed trust decision. */
    public data object Verifying : CompanionCertificateState

    /**
     * Platform trust validation and the authenticated KNet challenge both succeeded.
     *
     * @property rootCertificateSha256 Exact KNet root that was proven by the challenge.
     * @property verifiedAtEpochMillis Time at which the proof completed.
     */
    public data class Trusted(
        public val rootCertificateSha256: Sha256Fingerprint,
        public val verifiedAtEpochMillis: Long,
    ) : CompanionCertificateState {
        init {
            require(verifiedAtEpochMillis >= 0L) { "Certificate verification time must not be negative." }
        }
    }

    /** A challenge could not establish readiness; the typed reason remains presentation-safe. */
    public data class Rejected(public val reason: CompanionFailure) : CompanionCertificateState
}

/** Random, single-use challenge value echoed only by an authenticated KNet desktop. */
@JvmInline
public value class CompanionCertificateChallengeNonce(public val value: String) {
    init {
        require(value.length in 32..128 && value.all(Char::isChallengeCharacter)) {
            "Certificate challenge nonce must be a 32 to 128 character Base64URL value."
        }
    }
}

/** Versioned wire constants shared by desktop and mobile certificate-readiness adapters. */
public object CompanionCertificateProtocol {
    /** DNS identity carried in the KNet-CA-signed desktop challenge certificate. */
    public const val TLS_SERVER_NAME: String = "companion.knet.local"

    /** Authenticated endpoint that returns the exact KNet root certificate as DER bytes. */
    public const val ROOT_CERTIFICATE_PATH: String = "/companion/v1/certificates/root"

    /** Authenticated endpoint that echoes a fresh challenge after trusted TLS negotiation. */
    public const val TRUST_CHALLENGE_PATH: String = "/companion/v1/certificates/verify"

    /** Request and response header carrying the single-use challenge value. */
    public const val CHALLENGE_HEADER: String = "X-KNet-Certificate-Challenge"

    /** Maximum DER root size accepted in a pairing invitation or durable registration. */
    public const val MAXIMUM_ROOT_CERTIFICATE_BYTES: Int = 16 * 1024
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
    INVITATION_RETRIEVAL_FAILED,
    PAIRING_REJECTED,
    REGISTRATION_NOT_FOUND,
    CREDENTIAL_NOT_FOUND,
    CREDENTIAL_EXPIRED,
    NETWORK_UNAVAILABLE,
    TRANSPORT_UNAVAILABLE,
    TRANSPORT_IDENTITY_MISMATCH,
    DESKTOP_IDENTITY_CONFLICT,
    CERTIFICATE_UNAVAILABLE,
    CERTIFICATE_NOT_TRUSTED,
    VPN_PERMISSION_DENIED,
    VPN_START_FAILED,
    PLATFORM_ADAPTER_UNAVAILABLE,
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

private fun Char.isChallengeCharacter(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-' || this == '_'
