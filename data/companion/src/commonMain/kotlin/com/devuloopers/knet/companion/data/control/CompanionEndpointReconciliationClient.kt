package com.devuloopers.knet.companion.data.control

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.CancellationException

/** Shared endpoint client; native Ktor engines enforce the existing paired TLS pin before sending credentials. */
public class DefaultCompanionEndpointReconciliationClient(
    private val transport: CompanionControlTransport,
    private val codec: CompanionEndpointReconciliationCodec = CompanionEndpointReconciliationCodec(),
) : CompanionEndpointReconciliationClient {
    override suspend fun reconcile(
        registration: CompanionRegistration,
        candidateEndpoint: CompanionServiceEndpoint,
        credential: String,
    ): CompanionEndpointReconciliationResult = try {
        val response = transport.execute(
            CompanionControlRequest(
                endpoint = candidateEndpoint,
                transportIdentitySha256 = registration.transportIdentitySha256,
                rootCertificateSha256 = registration.rootCertificateSha256,
                rootCertificate = registration.rootCertificate,
                operation = CompanionControlOperation.RECONCILE_ENDPOINTS,
                body = codec.encodeRequest(CompanionEndpointReconciliationRequest(registration.desktopId)),
                authorization = CompanionControlAuthorization(registration.deviceId, credential),
            ),
        )
        if (response.statusCode !in 200..299) {
            CompanionEndpointReconciliationResult.Rejected(rejected(identityMismatch = response.statusCode == 409))
        } else {
            val descriptor = codec.decodeDescriptor(response.copyBody())
            if (!descriptor.accepts(registration.desktopId)) {
                CompanionEndpointReconciliationResult.Rejected(rejected(identityMismatch = true))
            } else {
                CompanionEndpointReconciliationResult.Verified(descriptor)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        CompanionEndpointReconciliationResult.Rejected(rejected(identityMismatch = false))
    }

    private fun rejected(identityMismatch: Boolean): CompanionFailure = CompanionFailure(
        code = if (identityMismatch) CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH else CompanionFailureCode.TRANSPORT_UNAVAILABLE,
        message = if (identityMismatch) {
            "Discovered desktop identity does not match the paired KNet desktop."
        } else {
            "Unable to verify the discovered KNet desktop endpoint."
        },
        recoverable = !identityMismatch,
    )
}
