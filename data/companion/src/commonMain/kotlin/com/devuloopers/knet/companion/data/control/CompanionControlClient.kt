package com.devuloopers.knet.companion.data.control

import com.devuloopers.knet.companion.application.contract.CompanionCredentialRefreshResult
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingCompletionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Secret-bearing control request. Transport implementations must redact body/authorization from logs. */
public class CompanionControlRequest(
    public val endpoint: CompanionServiceEndpoint,
    public val transportIdentitySha256: Sha256Fingerprint,
    public val path: String,
    body: ByteArray,
    public val authorizationCredential: String? = null,
) {
    private val content: ByteArray = body.copyOf()

    init {
        require(endpoint.secure)
        require(path.startsWith('/') && !path.contains(".."))
    }

    public fun copyBody(): ByteArray = content.copyOf()
}

/** Bounded control response; concrete transports enforce the configured byte limit before constructing it. */
public class CompanionControlResponse(
    public val statusCode: Int,
    body: ByteArray,
) {
    private val content: ByteArray = body.copyOf()

    init {
        require(statusCode in 100..599)
        require(content.size <= MAXIMUM_CONTROL_BODY_BYTES)
    }

    public fun copyBody(): ByteArray = content.copyOf()

    private companion object {
        const val MAXIMUM_CONTROL_BODY_BYTES: Int = 256 * 1024
    }
}

/** Pinned TLS request boundary implemented independently on Android and future iOS. */
public fun interface CompanionControlTransport {
    public suspend fun execute(request: CompanionControlRequest): CompanionControlResponse
}

/** Shared pairing protocol client; platform code supplies only proof signing and pinned TLS I/O. */
public class DefaultCompanionPairingClient(
    private val signer: CompanionDeviceProofSigner,
    private val transport: CompanionControlTransport,
    private val json: Json = com.devuloopers.knet.companion.data.defaultCompanionJson(),
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
        val payload = PairingRequestDto(
            invitationId = unsigned.invitationId.value,
            invitationSecret = unsigned.invitationSecret,
            deviceId = unsigned.deviceId.value,
            displayName = unsigned.displayName,
            publicKeyEncoded = unsigned.publicKeyEncoded,
            proofSignatureEncoded = signature,
            proofAlgorithm = unsigned.proofAlgorithm.name,
        )
        return try {
            val response = transport.execute(
                CompanionControlRequest(
                    endpoint = invitation.controlEndpoint,
                    transportIdentitySha256 = invitation.transportIdentitySha256,
                    path = PAIR_PATH,
                    body = json.encodeToString(payload).encodeToByteArray(),
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
                path = REFRESH_PATH,
                body = json.encodeToString(RefreshRequestDto(registration.deviceId.value)).encodeToByteArray(),
                authorizationCredential = currentCredential,
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
            val grant = json.decodeFromString<PairingGrantDto>(copyBody().decodeToString(throwOnInvalidSequence = true))
            CompanionPairingClientResult.Paired(
                credential = grant.credential,
                scopes = grant.scopes.map(DeviceScope::valueOf).toSet(),
                credentialExpiresAtEpochMillis = grant.credentialExpiresAtEpochMillis,
            )
        }.getOrElse { CompanionPairingClientResult.Rejected(pairingRejected(statusCode)) }
    }

    private fun CompanionControlResponse.toRefreshResult(): CompanionCredentialRefreshResult {
        if (statusCode !in 200..299) return CompanionCredentialRefreshResult.Rejected(pairingRejected(statusCode))
        return runCatching {
            val grant = json.decodeFromString<RefreshGrantDto>(copyBody().decodeToString(throwOnInvalidSequence = true))
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

    private companion object {
        const val PAIR_PATH: String = "/companion/v1/pair"
        const val REFRESH_PATH: String = "/companion/v1/credentials/refresh"
    }
}

@Serializable
private data class PairingRequestDto(
    @SerialName("invitation_id") val invitationId: String,
    @SerialName("invitation_secret") val invitationSecret: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("public_key_encoded") val publicKeyEncoded: String,
    @SerialName("proof_signature_encoded") val proofSignatureEncoded: String,
    @SerialName("proof_algorithm") val proofAlgorithm: String,
)

@Serializable
private data class PairingGrantDto(
    val credential: String,
    val scopes: List<String>,
    @SerialName("credential_expires_at_epoch_millis") val credentialExpiresAtEpochMillis: Long,
)

@Serializable
private data class RefreshRequestDto(
    @SerialName("device_id") val deviceId: String,
)

@Serializable
private data class RefreshGrantDto(
    val credential: String,
    @SerialName("credential_expires_at_epoch_millis") val credentialExpiresAtEpochMillis: Long,
)
