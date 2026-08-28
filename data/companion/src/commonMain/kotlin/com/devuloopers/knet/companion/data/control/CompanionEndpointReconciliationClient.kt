package com.devuloopers.knet.companion.data.control

import com.devuloopers.knet.companion.application.contract.CompanionControlAuthorization
import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationResult
import com.devuloopers.knet.companion.model.CompanionEndpointReconciliationCodec
import com.devuloopers.knet.companion.model.CompanionEndpointReconciliationRequest
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.core.logger.KNetLogger
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
        KNetLogger.debug(DISCOVERY_TAG) {
            "companion_event=endpoint_transport_started desktop_id=${registration.desktopId.value} " +
                "endpoint=${candidateEndpoint.host}:${candidateEndpoint.port}"
        }
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
            KNetLogger.warn(DISCOVERY_TAG) {
                "companion_event=endpoint_transport_rejected desktop_id=${registration.desktopId.value} " +
                    "endpoint=${candidateEndpoint.host}:${candidateEndpoint.port} status=${response.statusCode}"
            }
            CompanionEndpointReconciliationResult.Rejected(rejected(identityMismatch = response.statusCode == 409))
        } else {
            val descriptor = codec.decodeDescriptor(response.copyBody())
            if (!descriptor.accepts(registration.desktopId)) {
                KNetLogger.warn(DISCOVERY_TAG) {
                    "companion_event=endpoint_transport_rejected desktop_id=${registration.desktopId.value} " +
                        "endpoint=${candidateEndpoint.host}:${candidateEndpoint.port} reason=desktop_id"
                }
                CompanionEndpointReconciliationResult.Rejected(rejected(identityMismatch = true))
            } else {
                KNetLogger.info(DISCOVERY_TAG) {
                    "companion_event=endpoint_transport_verified desktop_id=${descriptor.desktopId.value} " +
                        "runtime_id=${descriptor.runtimeId.value} endpoint=${candidateEndpoint.host}:${candidateEndpoint.port}"
                }
                CompanionEndpointReconciliationResult.Verified(descriptor)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        KNetLogger.error(DISCOVERY_TAG, failure) {
            "companion_event=endpoint_transport_failed desktop_id=${registration.desktopId.value} " +
                "endpoint=${candidateEndpoint.host}:${candidateEndpoint.port} " +
                "reason=${failure::class.simpleName ?: "unknown"}"
        }
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

    private companion object {
        const val DISCOVERY_TAG: String = "CompanionDiscovery"
    }
}
