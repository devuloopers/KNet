package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.model.CompanionCertificateChallengeNonce
import com.devuloopers.knet.companion.model.CompanionRegistration
import java.security.cert.X509Certificate

/** Native TLS boundary used by Android certificate retrieval and trust verification. */
internal interface AndroidCertificateTlsClient {
    suspend fun executePinned(
        registration: CompanionRegistration,
        credential: String,
        path: String,
        challenge: CompanionCertificateChallengeNonce?,
        maximumBodyBytes: Int,
    ): AndroidCertificateTlsResult

    suspend fun executePlatformTrusted(
        registration: CompanionRegistration,
        credential: String,
        path: String,
        challenge: CompanionCertificateChallengeNonce,
        expectedRoot: X509Certificate,
        maximumBodyBytes: Int,
    ): AndroidCertificateTlsResult
}

/** Closed result set returned by the bounded Android certificate TLS client. */
internal sealed interface AndroidCertificateTlsResult {
    class Success(
        val statusCode: Int,
        val responseHeaders: Map<String, String>,
        body: ByteArray,
    ) : AndroidCertificateTlsResult {
        private val content: ByteArray = body.copyOf()
        val body: ByteArray get() = content.copyOf()
    }

    data object TrustRejected : AndroidCertificateTlsResult
    data object IdentityRejected : AndroidCertificateTlsResult
    data object Unavailable : AndroidCertificateTlsResult
}
