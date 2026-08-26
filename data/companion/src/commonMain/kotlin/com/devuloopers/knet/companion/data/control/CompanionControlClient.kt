package com.devuloopers.knet.companion.data.control

import com.devuloopers.knet.companion.application.contract.CompanionCredentialRefreshResult
import com.devuloopers.knet.companion.application.contract.CompanionControlAuthorization
import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.application.contract.CompanionControlResponse
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshGrantCodec
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshRequest
import com.devuloopers.knet.companion.model.CompanionCredentialRefreshRequestCodec
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionPairingCompletionCodec
import com.devuloopers.knet.companion.model.CompanionPairingGrantCodec
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.pairing.PairingCompletionRequest
import kotlinx.coroutines.CancellationException

/** Shared pairing protocol client; platform code supplies proof signing and paired-root TLS I/O. */
public class DefaultCompanionPairingClient(
    private val signer: CompanionDeviceProofSigner,
    private val transport: CompanionControlTransport,
    private val pairingRequestCodec: CompanionPairingCompletionCodec = CompanionPairingCompletionCodec(),
    private val pairingGrantCodec: CompanionPairingGrantCodec = CompanionPairingGrantCodec(),
    private val refreshRequestCodec: CompanionCredentialRefreshRequestCodec = CompanionCredentialRefreshRequestCodec(),
    private val refreshGrantCodec: CompanionCredentialRefreshGrantCodec = CompanionCredentialRefreshGrantCodec(),
) : CompanionPairingClient {
    override suspend fun pair(
        invitation: CompanionPairingInvitation,
        identity: CompanionDeviceIdentity,
        displayName: String,
    ): CompanionPairingClientResult {
        val unsigned = PairingCompletionRequest(
            invitationId = invitation.pairing.id,
            invitationSecret = invitation.pairing.secret,
            deviceId = identity.deviceId,
            displayName = displayName,
            publicKeyEncoded = identity.publicKeyEncoded,
            proofSignatureEncoded = "pending",
            proofAlgorithm = identity.proofAlgorithm,
        )
        val signature = signer.sign(identity, unsigned.proofMessage())
        val signed = PairingCompletionRequest(
            invitationId = unsigned.invitationId,
            invitationSecret = unsigned.invitationSecret,
            deviceId = unsigned.deviceId,
            displayName = unsigned.displayName,
            publicKeyEncoded = unsigned.publicKeyEncoded,
            proofSignatureEncoded = signature,
            proofAlgorithm = unsigned.proofAlgorithm,
        )
        return try {
            val response = transport.execute(
                CompanionControlRequest(
                    endpoint = invitation.controlEndpoint,
                    transportIdentitySha256 = invitation.transportIdentitySha256,
                    rootCertificateSha256 = invitation.rootCertificateSha256,
                    rootCertificate = invitation.rootCertificate,
                    operation = CompanionControlOperation.PAIR,
                    body = pairingRequestCodec.encode(signed),
                ),
            )
            response.toPairingResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            CompanionPairingClientResult.Rejected(transportFailure())
        }
    }

    override suspend fun refresh(
        registration: CompanionRegistration,
        currentCredential: String,
    ): CompanionCredentialRefreshResult = try {
        transport.execute(
            CompanionControlRequest(
                endpoint = registration.controlEndpoint,
                transportIdentitySha256 = registration.transportIdentitySha256,
                rootCertificateSha256 = registration.rootCertificateSha256,
                rootCertificate = registration.rootCertificate,
                operation = CompanionControlOperation.REFRESH_CREDENTIAL,
                body = refreshRequestCodec.encode(CompanionCredentialRefreshRequest(registration.deviceId)),
                authorization = CompanionControlAuthorization(registration.deviceId, currentCredential),
            ),
        ).toRefreshResult()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        CompanionCredentialRefreshResult.Rejected(transportFailure())
    }

    private fun CompanionControlResponse.toPairingResult(): CompanionPairingClientResult {
        if (statusCode !in 200..299) return CompanionPairingClientResult.Rejected(pairingRejected(statusCode))
        return runCatching {
            val grant = pairingGrantCodec.decode(copyBody())
            CompanionPairingClientResult.Paired(
                credential = grant.credential,
                scopes = grant.scopes,
                credentialExpiresAtEpochMillis = grant.credentialExpiresAtEpochMillis,
            )
        }.getOrElse { CompanionPairingClientResult.Rejected(pairingRejected(statusCode)) }
    }

    private fun CompanionControlResponse.toRefreshResult(): CompanionCredentialRefreshResult {
        if (statusCode !in 200..299) return CompanionCredentialRefreshResult.Rejected(pairingRejected(statusCode))
        return runCatching {
            val grant = refreshGrantCodec.decode(copyBody())
            CompanionCredentialRefreshResult.Refreshed(grant.credential, grant.credentialExpiresAtEpochMillis)
        }.getOrElse { CompanionCredentialRefreshResult.Rejected(pairingRejected(statusCode)) }
    }

    private fun transportFailure(): CompanionFailure = CompanionFailure(
        code = CompanionFailureCode.TRANSPORT_UNAVAILABLE,
        message = "Unable to reach the paired desktop securely.",
        recoverable = true,
    )

    private fun pairingRejected(statusCode: Int): CompanionFailure = CompanionFailure(
        code = CompanionFailureCode.PAIRING_REJECTED,
        message = "Desktop rejected the companion request (HTTP $statusCode).",
        recoverable = statusCode >= 500,
    )

}
