package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.model.CompanionCertificateChallengeNonce
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import java.security.SecureRandom
import kotlin.io.encoding.Base64

/** Android-policy TLS challenge verifier for one paired desktop identity. */
internal class AndroidCompanionCertificateTrustVerifier(
    private val client: AndroidCertificateTlsClient,
    private val nowEpochMillis: () -> Long,
) : CompanionCertificateTrustVerifier {
    override suspend fun verify(
        registration: CompanionRegistration,
        credential: String,
        rootCertificate: CompanionCertificateArtifact,
    ): CompanionCertificateState {
        KNetLogger.debug(LogTags.CERTIFICATE) { "companion_event=trust_challenge_started" }
        val root = rootCertificate.copyBytes().parseX509Certificate()
            ?: return rejectedAndroidCertificate("The downloaded KNet root certificate is invalid.").also {
                KNetLogger.warn(LogTags.CERTIFICATE) {
                    "companion_event=trust_challenge_rejected reason=invalid_root"
                }
            }
        if (root.sha256Hex() != registration.rootCertificateSha256.value) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "companion_event=trust_challenge_rejected reason=root_identity"
            }
            return rejectedAndroidCertificate("The downloaded KNet root certificate does not match this desktop.")
        }
        val challenge = CompanionCertificateChallengeNonce(randomChallenge())
        return when (
            val result = client.executePlatformTrusted(
                registration = registration,
                credential = credential,
                path = CompanionCertificateProtocol.TRUST_CHALLENGE_PATH,
                challenge = challenge,
                expectedRoot = root,
                maximumBodyBytes = MAXIMUM_CHALLENGE_BODY_BYTES,
            )
        ) {
            is AndroidCertificateTlsResult.Success -> {
                if (
                    result.statusCode in 200..299 &&
                    result.responseHeaders[CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase()] == challenge.value
                ) {
                    KNetLogger.info(LogTags.CERTIFICATE) { "companion_event=trust_challenge_completed result=trusted" }
                    CompanionCertificateState.Trusted(registration.rootCertificateSha256, nowEpochMillis())
                } else {
                    KNetLogger.warn(LogTags.CERTIFICATE) {
                        "companion_event=trust_challenge_rejected reason=invalid_response"
                    }
                    rejectedAndroidCertificate("The paired desktop returned an invalid certificate challenge response.")
                }
            }

            AndroidCertificateTlsResult.TrustRejected -> {
                KNetLogger.info(LogTags.CERTIFICATE) {
                    "companion_event=trust_challenge_completed result=installation_required"
                }
                CompanionCertificateState.InstallationRequired
            }
            AndroidCertificateTlsResult.IdentityRejected -> {
                KNetLogger.warn(LogTags.CERTIFICATE) {
                    "companion_event=trust_challenge_rejected reason=server_identity"
                }
                rejectedAndroidCertificate("The trusted server identity does not match the paired desktop.")
            }

            AndroidCertificateTlsResult.Unavailable -> {
                KNetLogger.error(LogTags.CERTIFICATE) {
                    "companion_event=trust_challenge_rejected reason=transport_unavailable"
                }
                CompanionCertificateState.Rejected(androidCertificateTransportUnavailable())
            }
        }
    }

    private fun randomChallenge(): String {
        val bytes = ByteArray(CHALLENGE_ENTROPY_BYTES).also(SecureRandom()::nextBytes)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).encode(bytes)
    }

    private companion object {
        private const val CHALLENGE_ENTROPY_BYTES: Int = 32
        private const val MAXIMUM_CHALLENGE_BODY_BYTES: Int = 1024
    }
}
