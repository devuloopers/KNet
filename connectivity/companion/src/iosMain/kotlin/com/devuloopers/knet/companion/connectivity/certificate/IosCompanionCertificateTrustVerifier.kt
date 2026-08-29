@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.http.sha256Hex
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/** iOS trust verifier that accepts only a platform-trusted TLS chain containing the paired KNet identities. */
internal class IosCompanionCertificateTrustVerifier(
    private val httpClient: KtorCompanionHttpClient,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CompanionCertificateTrustVerifier {
    override suspend fun verify(
        registration: CompanionRegistration,
        credential: String,
        rootCertificate: CompanionCertificateArtifact,
    ): CompanionCertificateState {
        val rootBytes = rootCertificate.copyBytes()
        if (
            rootBytes.sha256Hex() != registration.rootCertificateSha256.value ||
            !rootBytes.contentEquals(registration.rootCertificate.copyBytes())
        ) {
            return rejected(
                CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
                "The downloaded KNet root certificate does not match this desktop.",
                recoverable = false,
            )
        }
        val challenge = randomChallenge()
        return try {
            val response = httpClient.execute(
                CompanionHttpRequest(
                    endpoint = registration.controlEndpoint,
                    method = CompanionHttpMethod.POST,
                    path = CompanionCertificateProtocol.TRUST_CHALLENGE_PATH,
                    authorization = "Bearer ${registration.deviceId.value}:$credential",
                    additionalHeaders = mapOf(CompanionCertificateProtocol.CHALLENGE_HEADER to challenge),
                    maximumResponseBytes = MAXIMUM_CHALLENGE_BODY_BYTES,
                    security = CompanionHttpSecurity.PlatformTrusted(
                        expectedRootCertificate = registration.rootCertificate,
                        expectedRootCertificateSha256 = registration.rootCertificateSha256,
                        transportIdentitySha256 = registration.transportIdentitySha256,
                    ),
                ),
            )
            if (
                response.statusCode in 200..299 &&
                response.headers[CompanionCertificateProtocol.CHALLENGE_HEADER.lowercase()] == challenge
            ) {
                CompanionCertificateState.Trusted(
                    registration.rootCertificateSha256,
                    nowEpochMillis(),
                )
            } else {
                rejected(
                    CompanionFailureCode.CERTIFICATE_NOT_TRUSTED,
                    "The paired desktop returned an invalid certificate challenge response.",
                    recoverable = true,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CompanionHttpSecurityException.TrustRejected) {
            CompanionCertificateState.InstallationRequired
        } catch (_: CompanionHttpSecurityException.IdentityRejected) {
            rejected(
                CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
                "The trusted server identity does not match the paired desktop.",
                recoverable = false,
            )
        } catch (_: Throwable) {
            CompanionCertificateState.VerificationDeferred(
                CompanionFailure(
                    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
                    "Unable to reach the paired desktop securely.",
                    recoverable = true,
                ),
            )
        }
    }

    private fun randomChallenge(): String {
        val bytes = ByteArray(CHALLENGE_ENTROPY_BYTES)
        val status = bytes.usePinned { pinned ->
            SecRandomCopyBytes(
                kSecRandomDefault,
                bytes.size.toULong(),
                pinned.addressOf(0).reinterpret<UByteVar>(),
            )
        }
        check(status == errSecSuccess) { "iOS could not produce certificate-challenge entropy." }
        return URL_SAFE_BASE64.encode(bytes)
    }

    private fun rejected(
        code: CompanionFailureCode,
        message: String,
        recoverable: Boolean,
    ): CompanionCertificateState.Rejected = CompanionCertificateState.Rejected(
        CompanionFailure(code, message, recoverable),
    )

    private companion object {
        const val CHALLENGE_ENTROPY_BYTES: Int = 32
        const val MAXIMUM_CHALLENGE_BODY_BYTES: Int = 1024
        val URL_SAFE_BASE64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}
