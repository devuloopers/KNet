package com.devuloopers.knet.companion.connectivity.bootstrap

import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolutionResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.model.CompanionBootstrapProtocol
import com.devuloopers.knet.companion.model.CompanionBootstrapRedemptionCodec
import com.devuloopers.knet.companion.model.CompanionBootstrapRedemptionRequest
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInvitationResponseCodec
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import kotlinx.coroutines.CancellationException

/** Portable invitation resolver backed by a platform-secured Ktor bootstrap exchange. */
internal class DefaultCompanionInvitationResolver(
    private val bootstrapClient: CompanionBootstrapClient,
    private val redemptionCodec: CompanionBootstrapRedemptionCodec = CompanionBootstrapRedemptionCodec(),
    private val invitationCodec: CompanionInvitationResponseCodec = CompanionInvitationResponseCodec(),
) : CompanionInvitationResolver {
    override suspend fun resolve(bootstrap: CompanionPairingBootstrap): CompanionInvitationResolutionResult {
        val body = redemptionCodec.encode(
            CompanionBootstrapRedemptionRequest(bootstrap.id, bootstrap.retrievalSecret),
        )
        return try {
            when (val response = bootstrapClient.redeem(bootstrap, body)) {
                CompanionBootstrapResult.IdentityRejected -> rejected(
                    CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
                    "The desktop identity does not match the scanned invitation.",
                    recoverable = false,
                )
                CompanionBootstrapResult.Unavailable -> rejected(
                    CompanionFailureCode.INVITATION_RETRIEVAL_FAILED,
                    "Unable to retrieve pairing details from the desktop.",
                    recoverable = true,
                )
                is CompanionBootstrapResult.Response -> {
                    if (
                        response.statusCode != 200 ||
                        response.mediaType != CompanionBootstrapProtocol.RESPONSE_MEDIA_TYPE
                    ) {
                        rejected(
                            CompanionFailureCode.INVITATION_RETRIEVAL_FAILED,
                            "The pairing invitation is unavailable, expired, or already used.",
                            recoverable = response.statusCode >= 500,
                        )
                    } else {
                        runCatching { invitationCodec.decode(response.copyBody()) }
                            .fold(
                                onSuccess = CompanionInvitationResolutionResult::Resolved,
                                onFailure = {
                                    rejected(
                                        CompanionFailureCode.INVITATION_INVALID,
                                        "Desktop returned invalid pairing details.",
                                        recoverable = false,
                                    )
                                },
                            )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            rejected(
                CompanionFailureCode.INVITATION_RETRIEVAL_FAILED,
                "Unable to retrieve pairing details from the desktop.",
                recoverable = true,
            )
        }
    }

    private fun rejected(
        code: CompanionFailureCode,
        message: String,
        recoverable: Boolean,
    ): CompanionInvitationResolutionResult.Rejected = CompanionInvitationResolutionResult.Rejected(
        CompanionFailure(code, message, recoverable),
    )
}

/** Narrow bootstrap transport retained for deterministic resolver tests. */
internal fun interface CompanionBootstrapClient {
    suspend fun redeem(bootstrap: CompanionPairingBootstrap, body: ByteArray): CompanionBootstrapResult
}

/** Bounded result of one portable bootstrap exchange. */
internal sealed interface CompanionBootstrapResult {
    class Response(
        val statusCode: Int,
        val mediaType: String?,
        body: ByteArray,
    ) : CompanionBootstrapResult {
        private val content: ByteArray = body.copyOf()

        fun copyBody(): ByteArray = content.copyOf()
    }

    data object IdentityRejected : CompanionBootstrapResult
    data object Unavailable : CompanionBootstrapResult
}
