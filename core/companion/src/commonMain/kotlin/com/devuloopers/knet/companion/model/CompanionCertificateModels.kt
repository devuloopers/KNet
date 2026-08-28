package com.devuloopers.knet.companion.model

/** Immutable DER-encoded public root certificate carried by a companion pairing contract. */
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

/** Certificate-installation capability, separate from actual end-to-end trust verification. */
public sealed interface CompanionCertificateState {
    public data object Unknown : CompanionCertificateState
    public data object InstallationRequired : CompanionCertificateState
    public data object Verifying : CompanionCertificateState

    /** A successful end-to-end platform trust verification. */
    public data class Trusted(
        public val rootCertificateSha256: Sha256Fingerprint,
        public val verifiedAtEpochMillis: Long,
    ) : CompanionCertificateState {
        init {
            require(verifiedAtEpochMillis >= 0L) { "Certificate verification time must not be negative." }
        }
    }

    public data class Rejected(public val reason: CompanionFailure) : CompanionCertificateState
}

/** Versioned wire constants shared by desktop and mobile certificate-readiness adapters. */
public object CompanionCertificateProtocol {
    public const val TLS_SERVER_NAME: String = "companion.knet.local"
    public const val ROOT_CERTIFICATE_PATH: String = "/companion/v1/certificates/root"
    public const val ROOT_CERTIFICATE_MEDIA_TYPE: String = "application/x-x509-ca-cert"
    public const val APPLE_PROFILE_PATH: String = "/companion/v1/certificates/root.mobileconfig"
    public const val APPLE_PROFILE_MEDIA_TYPE: String = "application/x-apple-aspen-config"
    public const val TRUST_CHALLENGE_PATH: String = "/companion/v1/certificates/verify"
    public const val CHALLENGE_HEADER: String = "X-KNet-Certificate-Challenge"
    public const val MAXIMUM_ROOT_CERTIFICATE_BYTES: Int = 16 * 1024
    public const val MAXIMUM_INSTALLATION_ARTIFACT_BYTES: Int = 64 * 1024
}
