package com.devuloopers.knet.companion.connectivity.certificate

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags

/** Android authenticated source for the paired desktop's exact public root certificate. */
internal class AndroidCompanionRootCertificateSource(
    private val client: AndroidCertificateTlsClient,
) : CompanionRootCertificateSource, CompanionCertificateInstallationArtifactSource {
    override suspend fun download(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionCertificateDownloadResult = when (
        val result = client.executePinned(
            registration = registration,
            credential = credential,
            path = CompanionCertificateProtocol.ROOT_CERTIFICATE_PATH,
            challenge = null,
            maximumBodyBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
        )
    ) {
        is AndroidCertificateTlsResult.Success -> validateRoot(registration, result)
        AndroidCertificateTlsResult.IdentityRejected -> CompanionCertificateDownloadResult.Failed(
            CompanionFailure(
                CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
                "The desktop certificate identity did not match the pairing invitation.",
                false,
            ),
        )

        AndroidCertificateTlsResult.TrustRejected,
        AndroidCertificateTlsResult.Unavailable,
            -> CompanionCertificateDownloadResult.Failed(androidCertificateTransportUnavailable())
    }

    private fun validateRoot(
        registration: CompanionRegistration,
        response: AndroidCertificateTlsResult.Success,
    ): CompanionCertificateDownloadResult {
        if (response.statusCode !in 200..299) {
            KNetLogger.warn(LogTags.CERTIFICATE) {
                "companion_event=root_rejected reason=http_status status=${response.statusCode}"
            }
            return CompanionCertificateDownloadResult.Failed(androidCertificateTransportUnavailable())
        }
        val mediaType = response.responseHeaders["content-type"]
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (mediaType != CompanionCertificateProtocol.ROOT_CERTIFICATE_MEDIA_TYPE) {
            KNetLogger.warn(LogTags.CERTIFICATE) { "companion_event=root_rejected reason=media_type" }
            return CompanionCertificateDownloadResult.Failed(androidCertificateTransportUnavailable())
        }
        val certificate = response.body.parseX509Certificate()
        if (certificate == null) {
            KNetLogger.warn(LogTags.CERTIFICATE) { "companion_event=root_rejected reason=invalid_material" }
            return CompanionCertificateDownloadResult.Failed(
                CompanionFailure(
                    CompanionFailureCode.CERTIFICATE_UNAVAILABLE,
                    "The paired desktop returned invalid root-certificate material.",
                    false,
                ),
            )
        }
        if (
            !certificate.isValidPairingRoot(registration.rootCertificateSha256.value) ||
            !certificate.encoded.contentEquals(registration.rootCertificate.copyBytes())
        ) {
            KNetLogger.warn(LogTags.CERTIFICATE) { "companion_event=root_rejected reason=identity_mismatch" }
            return CompanionCertificateDownloadResult.Failed(
                CompanionFailure(
                    CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
                    "The desktop root certificate did not match the paired identity.",
                    false,
                ),
            )
        }
        return CompanionCertificateDownloadResult.Downloaded(
            CompanionCertificateArtifact(response.body, ROOT_CERTIFICATE_FILE_NAME),
        )
    }

    private companion object {
        private const val ROOT_CERTIFICATE_FILE_NAME: String = "knet-root-ca.crt"
    }
}
