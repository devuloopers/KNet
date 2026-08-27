package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.flow.Flow

/** Immutable public certificate-installation artifact delivered by an authenticated desktop. */
public class CompanionCertificateArtifact(
    bytes: ByteArray,
    /** Safe file-name suggestion for a platform-owned public certificate artifact. */
    public val suggestedFileName: String,
) {
    private val content: ByteArray = bytes.copyOf()

    init {
        require(content.size in 1..CompanionCertificateProtocol.MAXIMUM_INSTALLATION_ARTIFACT_BYTES)
        require(suggestedFileName.isNotBlank())
    }

    /** Returns a defensive copy of the public artifact bytes. */
    public fun copyBytes(): ByteArray = content.copyOf()

    override fun equals(other: Any?): Boolean =
        other is CompanionCertificateArtifact &&
            suggestedFileName == other.suggestedFileName &&
            content.contentEquals(other.content)

    override fun hashCode(): Int = 31 * content.contentHashCode() + suggestedFileName.hashCode()
}

/** Platform-selected authenticated source for the certificate artifact the user installs. */
public fun interface CompanionCertificateInstallationArtifactSource {
    /**
     * Downloads the native installation artifact after authenticating with the paired credential.
     *
     * Android implementations return DER certificate bytes; Darwin implementations return a `.mobileconfig`
     * profile. Implementations must validate the pinned control identity before transmitting [credential].
     */
    public suspend fun download(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionCertificateDownloadResult
}

/** Authenticated source for the paired desktop's public KNet root certificate. */
public fun interface CompanionRootCertificateSource {
    /**
     * Downloads root material after authenticating with the paired credential.
     *
     * Implementations must validate the pinned control identity before transmitting [credential] and must never
     * log or retain the credential.
     */
    public suspend fun download(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionCertificateDownloadResult
}

/** Platform trust adapter that proves the expected KNet root through a real TLS challenge. */
public fun interface CompanionCertificateTrustVerifier {
    /**
     * Verifies platform trust, the expected root, the authenticated desktop identity, and a fresh challenge.
     *
     * [credential] and [rootCertificate] are ephemeral inputs and must not enter observable or durable state.
     */
    public suspend fun verify(
        registration: CompanionRegistration,
        credential: String,
        rootCertificate: CompanionCertificateArtifact,
    ): CompanionCertificateState
}

/** Platform notification boundary used only to trigger another authoritative TLS verification. */
public fun interface CompanionCertificateStoreChangeObserver {
    /** Emits after the platform reports a possible trust-store change; it never asserts that KNet is trusted. */
    public fun observeChanges(): Flow<Unit>
}

/** Certificate download outcome. */
public sealed interface CompanionCertificateDownloadResult {
    /** Authenticated public certificate material ready for validation or platform installation. */
    public data class Downloaded(public val artifact: CompanionCertificateArtifact) : CompanionCertificateDownloadResult

    /** Typed, presentation-safe reason the certificate artifact could not be retrieved. */
    public data class Failed(public val failure: CompanionFailure) : CompanionCertificateDownloadResult
}
