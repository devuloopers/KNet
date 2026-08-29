package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.http.sha256Hex
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlin.coroutines.cancellation.CancellationException

/** Authenticated source for the exact DER root pinned by an iOS companion registration. */
internal class IosCompanionRootCertificateSource(
    private val httpClient: KtorCompanionHttpClient,
) : CompanionRootCertificateSource {
    override suspend fun download(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionCertificateDownloadResult = try {
        val response = httpClient.execute(
            CompanionHttpRequest(
                endpoint = registration.controlEndpoint,
                method = CompanionHttpMethod.GET,
                path = CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH,
                acceptedMediaType = CompanionCertificateProtocol.ROOT_CERTIFICATE_MEDIA_TYPE,
                authorization = "Bearer ${registration.deviceId.value}:$credential",
                maximumResponseBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
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
            response.mediaType != CompanionCertificateProtocol.ROOT_CERTIFICATE_MEDIA_TYPE ->
                failed(CompanionFailureCode.CERTIFICATE_UNAVAILABLE)
            body.sha256Hex() != registration.rootCertificateSha256.value ||
                !body.contentEquals(registration.rootCertificate.copyBytes()) ->
                failed(CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH)
            else -> CompanionCertificateDownloadResult.Downloaded(
                CompanionCertificateArtifact(body, ROOT_CERTIFICATE_FILE_NAME),
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
                        "The desktop root certificate did not match the paired identity."
                    CompanionFailureCode.TRANSPORT_UNAVAILABLE ->
                        "Unable to reach the paired desktop securely."
                    else -> "The paired desktop root certificate is unavailable."
                },
                recoverable = code != CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
            ),
        )

    private companion object {
        const val ROOT_CERTIFICATE_FILE_NAME: String = "knet-root-ca.crt"
    }
}
