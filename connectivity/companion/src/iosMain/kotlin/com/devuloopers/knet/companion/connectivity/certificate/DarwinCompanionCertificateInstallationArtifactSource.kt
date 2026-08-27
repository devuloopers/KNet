package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/** Darwin source for the authenticated Apple configuration profile installed through system Settings. */
internal class DarwinCompanionCertificateInstallationArtifactSource(
    private val httpClient: KtorCompanionHttpClient,
) : CompanionCertificateInstallationArtifactSource {
    override suspend fun download(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionCertificateDownloadResult = try {
        val response = httpClient.execute(
            CompanionHttpRequest(
                endpoint = registration.controlEndpoint,
                method = CompanionHttpMethod.GET,
                path = CompanionCertificateProtocol.APPLE_PROFILE_PATH,
                acceptedMediaType = CompanionCertificateProtocol.APPLE_PROFILE_MEDIA_TYPE,
                authorization = "Bearer ${registration.deviceId.value}:$credential",
                maximumResponseBytes = CompanionCertificateProtocol.MAXIMUM_INSTALLATION_ARTIFACT_BYTES,
                security = CompanionHttpSecurity.PinnedRoot(
                    rootCertificate = registration.rootCertificate,
                    rootCertificateSha256 = registration.rootCertificateSha256,
                    transportIdentitySha256 = registration.transportIdentitySha256,
                ),
            ),
        )
        val body = response.copyBody()
        when {
            response.statusCode !in 200..299 -> failed(CompanionFailureCode.CERTIFICATE_UNAVAILABLE)
            response.mediaType != CompanionCertificateProtocol.APPLE_PROFILE_MEDIA_TYPE ->
                failed(CompanionFailureCode.CERTIFICATE_UNAVAILABLE)
            !body.isExpectedAppleRootProfile(registration.rootCertificate.copyBytes()) ->
                failed(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH)
            else -> CompanionCertificateDownloadResult.Downloaded(
                CompanionCertificateArtifact(body, APPLE_PROFILE_FILE_NAME),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: CompanionHttpSecurityException.IdentityRejected) {
        failed(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH)
    } catch (_: CompanionHttpSecurityException.TrustRejected) {
        failed(CompanionFailureCode.TRANSPORT_UNAVAILABLE)
    } catch (_: Throwable) {
        failed(CompanionFailureCode.CERTIFICATE_UNAVAILABLE)
    }

    private fun failed(code: CompanionFailureCode): CompanionCertificateDownloadResult.Failed =
        CompanionCertificateDownloadResult.Failed(
            CompanionFailure(
                code = code,
                message = when (code) {
                    CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH ->
                        "The desktop certificate profile did not match the paired identity."
                    CompanionFailureCode.TRANSPORT_UNAVAILABLE ->
                        "Unable to reach the paired desktop securely."
                    else -> "The paired desktop certificate profile is unavailable."
                },
                recoverable = code != CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
            ),
        )

    private companion object {
        private const val APPLE_PROFILE_FILE_NAME: String = "knet-ca.mobileconfig"
    }
}

private fun ByteArray.isExpectedAppleRootProfile(expectedRoot: ByteArray): Boolean {
    val profile = runCatching { decodeToString() }.getOrNull() ?: return false
    return "com.apple.security.root" in profile &&
        Base64.encode(expectedRoot) in profile &&
        "{{certificateBase64}}" !in profile
}
