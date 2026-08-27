package com.devuloopers.knet.companion.connectivity.control

import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.application.contract.CompanionControlResponse
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpMethod
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpRequest
import com.devuloopers.knet.companion.connectivity.http.CompanionHttpSecurity
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.model.CompanionControlProtocol

/** Portable control transport whose Ktor engine is configured by the active native platform. */
internal class KtorCompanionControlTransport(
    private val httpClient: KtorCompanionHttpClient,
) : CompanionControlTransport {
    override suspend fun execute(request: CompanionControlRequest): CompanionControlResponse {
        val operation = request.operation.toWireOperation()
        val response = httpClient.execute(
            CompanionHttpRequest(
                endpoint = request.endpoint,
                method = CompanionHttpMethod.POST,
                path = operation.path,
                requestMediaType = operation.requestMediaType,
                acceptedMediaType = operation.responseMediaType,
                authorization = request.authorization?.let { authorization ->
                    "Bearer ${authorization.deviceId.value}:${authorization.credential()}"
                },
                body = request.copyBody(),
                maximumResponseBytes = CompanionControlProtocol.MAXIMUM_RESPONSE_BYTES,
                security = CompanionHttpSecurity.PinnedRoot(
                    rootCertificate = request.rootCertificate,
                    rootCertificateSha256 = request.rootCertificateSha256,
                    transportIdentitySha256 = request.transportIdentitySha256,
                ),
            ),
        )
        if (response.statusCode in 200..299) {
            require(response.mediaType == operation.responseMediaType) {
                "Companion control response media type is invalid."
            }
        }
        return CompanionControlResponse(response.statusCode, response.copyBody())
    }

    private fun CompanionControlOperation.toWireOperation(): ControlWireOperation = when (this) {
        CompanionControlOperation.PAIR -> ControlWireOperation(
            path = CompanionControlProtocol.PAIR_PATH,
            requestMediaType = CompanionControlProtocol.PAIR_REQUEST_MEDIA_TYPE,
            responseMediaType = CompanionControlProtocol.PAIR_RESPONSE_MEDIA_TYPE,
        )
        CompanionControlOperation.REFRESH_CREDENTIAL -> ControlWireOperation(
            path = CompanionControlProtocol.REFRESH_PATH,
            requestMediaType = CompanionControlProtocol.REFRESH_REQUEST_MEDIA_TYPE,
            responseMediaType = CompanionControlProtocol.REFRESH_RESPONSE_MEDIA_TYPE,
        )
        CompanionControlOperation.RECONCILE_ENDPOINTS -> ControlWireOperation(
            path = CompanionControlProtocol.RECONCILE_PATH,
            requestMediaType = CompanionControlProtocol.RECONCILE_REQUEST_MEDIA_TYPE,
            responseMediaType = CompanionControlProtocol.RECONCILE_RESPONSE_MEDIA_TYPE,
        )
    }

    private data class ControlWireOperation(
        val path: String,
        val requestMediaType: String,
        val responseMediaType: String,
    )
}
