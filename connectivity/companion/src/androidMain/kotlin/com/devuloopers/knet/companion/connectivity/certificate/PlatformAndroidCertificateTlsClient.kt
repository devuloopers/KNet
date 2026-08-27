package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.model.CompanionCertificateChallengeNonce
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import java.security.cert.X509Certificate
import kotlinx.coroutines.CancellationException

/** Android certificate client implemented through the shared bounded Ktor exchange. */
internal class PlatformAndroidCertificateTlsClient(
    private val httpClient: KtorCompanionHttpClient,
) : AndroidCertificateTlsClient {
    override suspend fun executePinned(
        registration: CompanionRegistration,
        credential: String,
        path: String,
        challenge: CompanionCertificateChallengeNonce?,
        maximumBodyBytes: Int,
    ): AndroidCertificateTlsResult = execute(
        registration = registration,
        credential = credential,
        path = path,
        challenge = challenge,
        maximumBodyBytes = maximumBodyBytes,
        security = CompanionHttpSecurity.PinnedRoot(
            registration.rootCertificate,
            registration.rootCertificateSha256,
            registration.transportIdentitySha256,
        ),
    )

    override suspend fun executePlatformTrusted(
        registration: CompanionRegistration,
        credential: String,
        path: String,
        challenge: CompanionCertificateChallengeNonce,
        expectedRoot: X509Certificate,
        maximumBodyBytes: Int,
    ): AndroidCertificateTlsResult = execute(
        registration = registration,
        credential = credential,
        path = path,
        challenge = challenge,
        maximumBodyBytes = maximumBodyBytes,
        security = CompanionHttpSecurity.PlatformTrusted(
            CompanionRootCertificate(expectedRoot.encoded),
            registration.rootCertificateSha256,
            registration.transportIdentitySha256,
        ),
    )

    private suspend fun execute(
        registration: CompanionRegistration,
        credential: String,
        path: String,
        challenge: CompanionCertificateChallengeNonce?,
        maximumBodyBytes: Int,
        security: CompanionHttpSecurity,
    ): AndroidCertificateTlsResult {
        require(credential.matches(SAFE_CREDENTIAL)) { "Companion credential contains unsafe HTTP characters." }
        require(maximumBodyBytes in 0..CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES)
        val trustMode = when (security) {
            CompanionHttpSecurity.BootstrapRootOnly -> "bootstrap_root"
            is CompanionHttpSecurity.PinnedRoot -> "pinned_root"
            is CompanionHttpSecurity.PlatformTrusted -> "platform_trusted"
        }
        return try {
            val response = httpClient.execute(
                CompanionHttpRequest(
                    endpoint = registration.controlEndpoint,
                    method = if (challenge == null) CompanionHttpMethod.GET else CompanionHttpMethod.POST,
                    path = path,
                    acceptedMediaType = if (challenge == null) {
                        CompanionCertificateProtocol.ROOT_CERTIFICATE_MEDIA_TYPE
                    } else {
                        "application/octet-stream"
                    },
                    authorization = "Bearer ${registration.deviceId.value}:$credential",
                    additionalHeaders = challenge?.let { nonce ->
                        mapOf(CompanionCertificateProtocol.CHALLENGE_HEADER to nonce.value)
                    }.orEmpty(),
                    maximumResponseBytes = maximumBodyBytes,
                    security = security,
                ),
            )
            AndroidCertificateTlsResult.Success(
                statusCode = response.statusCode,
                responseHeaders = response.headers,
                body = response.copyBody(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CompanionHttpSecurityException.IdentityRejected) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "companion_event=transport_rejected trust_mode=$trustMode reason=identity"
            }
            AndroidCertificateTlsResult.IdentityRejected
        } catch (_: CompanionHttpSecurityException.TrustRejected) {
            KNetLogger.info(LogTags.CERTIFICATE) {
                "companion_event=transport_rejected trust_mode=$trustMode reason=platform_trust"
            }
            AndroidCertificateTlsResult.TrustRejected
        } catch (failure: Exception) {
            KNetLogger.error(LogTags.CERTIFICATE) {
                "companion_event=transport_unavailable trust_mode=$trustMode " +
                    "reason=${failure::class.simpleName ?: "unknown"}"
            }
            AndroidCertificateTlsResult.Unavailable
        }
    }

    private companion object {
        private val SAFE_CREDENTIAL: Regex = Regex("[A-Za-z0-9._~-]{1,512}")
    }
}
