package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.flow.Flow

/** Immutable public root-certificate bytes delivered by an authenticated desktop. */
public class CompanionCertificateArtifact(
    bytes: ByteArray,
    public val suggestedFileName: String,
) {
    private val content: ByteArray = bytes.copyOf()

    init {
        require(content.isNotEmpty())
        require(suggestedFileName.isNotBlank())
    }

    public fun copyBytes(): ByteArray = content.copyOf()

    override fun equals(other: Any?): Boolean =
        other is CompanionCertificateArtifact &&
            suggestedFileName == other.suggestedFileName &&
            content.contentEquals(other.content)

    override fun hashCode(): Int = 31 * content.contentHashCode() + suggestedFileName.hashCode()
}

/** Certificate download and end-to-end trust challenge boundary. */
public interface CompanionCertificateController {
    public fun observe(registration: CompanionRegistration): Flow<CompanionCertificateState>
    public suspend fun download(registration: CompanionRegistration): CompanionCertificateDownloadResult
    public suspend fun verifyTrust(registration: CompanionRegistration): CompanionCertificateState
}

/** Certificate download outcome. */
public sealed interface CompanionCertificateDownloadResult {
    public data class Downloaded(public val artifact: CompanionCertificateArtifact) : CompanionCertificateDownloadResult
    public data class Failed(public val failure: CompanionFailure) : CompanionCertificateDownloadResult
}
