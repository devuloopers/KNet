package com.devuloopers.knet.companion.connectivity.bootstrap

import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurityException
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.http.sha256Hex
import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import kotlinx.coroutines.CancellationException

/** Ktor bootstrap client that authenticates downloaded public roots before transmitting invitation secrets. */
internal class KtorCompanionBootstrapClient(
    private val httpClient: KtorCompanionHttpClient,
) : CompanionBootstrapClient {
    override suspend fun redeem(
        bootstrap: CompanionPairingBootstrap,
        body: ByteArray,
    ): CompanionBootstrapResult {
        if (body.size > CompanionBootstrapProtocol.MAXIMUM_REQUEST_BYTES) return CompanionBootstrapResult.Unavailable
        return try {
            val rootResponse = httpClient.execute(
                CompanionHttpRequest(
                    endpoint = bootstrap.rootCertificateEndpoint,
                    method = CompanionHttpMethod.GET,
                    path = CompanionBootstrapProtocol.ROOT_CERTIFICATE_PATH,
                    acceptedMediaType = CompanionBootstrapProtocol.ROOT_CERTIFICATE_MEDIA_TYPE,
                    maximumResponseBytes = CompanionCertificateProtocol.MAXIMUM_ROOT_CERTIFICATE_BYTES,
                    security = CompanionHttpSecurity.BootstrapRootOnly,
                ),
            )
            val rootBytes = rootResponse.copyBody()
            if (
                rootResponse.statusCode != 200 ||
                rootResponse.mediaType != CompanionBootstrapProtocol.ROOT_CERTIFICATE_MEDIA_TYPE ||
                rootBytes.sha256Hex() != bootstrap.rootCertificateSha256.value
            ) {
                return CompanionBootstrapResult.IdentityRejected
            }
            val redemption = httpClient.execute(
                CompanionHttpRequest(
                    endpoint = bootstrap.retrievalEndpoint,
                    method = CompanionHttpMethod.POST,
                    path = CompanionBootstrapProtocol.REDEEM_PATH,
                    requestMediaType = CompanionBootstrapProtocol.REQUEST_MEDIA_TYPE,
                    acceptedMediaType = CompanionBootstrapProtocol.RESPONSE_MEDIA_TYPE,
                    body = body,
                    maximumResponseBytes = CompanionBootstrapProtocol.MAXIMUM_RESPONSE_BYTES,
                    security = CompanionHttpSecurity.PinnedRoot(
                        rootCertificate = CompanionRootCertificate(rootBytes),
                        rootCertificateSha256 = bootstrap.rootCertificateSha256,
                        transportIdentitySha256 = bootstrap.transportIdentitySha256,
                    ),
                ),
            )
            CompanionBootstrapResult.Response(redemption.statusCode, redemption.mediaType, redemption.copyBody())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CompanionHttpSecurityException) {
            CompanionBootstrapResult.IdentityRejected
        } catch (_: Throwable) {
            CompanionBootstrapResult.Unavailable
        }
    }
}
