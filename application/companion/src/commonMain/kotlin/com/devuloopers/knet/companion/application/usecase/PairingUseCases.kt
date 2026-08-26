package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.InvitationDecodeResult
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Validates a scanned or pasted invitation without retaining its secret. */
public class AcceptPairingInvitationUseCase(
    private val codec: CompanionInvitationCodec,
    private val nowEpochMillis: () -> Long,
) {
    public fun execute(payload: String): AcceptPairingInvitationResult {
        if (payload.isBlank()) return AcceptPairingInvitationResult.Rejected(
            CompanionFailure(CompanionFailureCode.INVITATION_INVALID, "Pairing invitation is empty.", false),
        )
        return when (val decoded = codec.decode(payload.trim())) {
            is InvitationDecodeResult.Rejected -> AcceptPairingInvitationResult.Rejected(decoded.failure)
            is InvitationDecodeResult.Accepted -> {
                if (nowEpochMillis() >= decoded.invitation.pairing.expiresAtEpochMillis) {
                    AcceptPairingInvitationResult.Rejected(
                        CompanionFailure(CompanionFailureCode.INVITATION_EXPIRED, "Pairing invitation expired.", false),
                    )
                } else {
                    AcceptPairingInvitationResult.Accepted(decoded.invitation)
                }
            }
        }
    }
}

/** In-memory invitation validation outcome. */
public sealed interface AcceptPairingInvitationResult {
    public data class Accepted(public val invitation: CompanionPairingInvitation) : AcceptPairingInvitationResult
    public data class Rejected(public val failure: CompanionFailure) : AcceptPairingInvitationResult
}

/** Completes pairing and commits credential plus non-secret registration with rollback on failure. */
public class PairCompanionDeviceUseCase(
    private val identityProvider: CompanionDeviceIdentityProvider,
    private val pairingClient: CompanionPairingClient,
    private val credentials: CompanionCredentialStore,
    private val registrations: CompanionRegistrationRepository,
    private val nowEpochMillis: () -> Long,
) {
    public suspend fun execute(
        invitation: CompanionPairingInvitation,
        deviceDisplayName: String,
    ): PairCompanionDeviceResult {
        if (deviceDisplayName.isBlank() || deviceDisplayName.length > 128) {
            return PairCompanionDeviceResult.Rejected(
                CompanionFailure(CompanionFailureCode.PAIRING_REJECTED, "Device name must contain 1 to 128 characters.", false),
            )
        }
        if (nowEpochMillis() >= invitation.pairing.expiresAtEpochMillis) {
            return PairCompanionDeviceResult.Rejected(
                CompanionFailure(CompanionFailureCode.INVITATION_EXPIRED, "Pairing invitation expired.", false),
            )
        }
        val identity: CompanionDeviceIdentity
        val paired: CompanionPairingClientResult
        try {
            identity = identityProvider.getOrCreate()
            paired = pairingClient.pair(invitation, identity, deviceDisplayName.trim())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return PairCompanionDeviceResult.Rejected(
                CompanionFailure(CompanionFailureCode.PAIRING_REJECTED, "Unable to complete secure device pairing.", true),
            )
        }
        return when (paired) {
            is CompanionPairingClientResult.Rejected -> PairCompanionDeviceResult.Rejected(paired.failure)
            is CompanionPairingClientResult.Paired -> {
                val pairedAt = nowEpochMillis()
                if (
                    paired.credential.isBlank() ||
                    paired.credentialExpiresAtEpochMillis <= pairedAt ||
                    paired.scopes.isEmpty() ||
                    !invitation.pairing.scopes.containsAll(paired.scopes)
                ) {
                    return PairCompanionDeviceResult.Rejected(
                        CompanionFailure(CompanionFailureCode.PAIRING_REJECTED, "Desktop returned an invalid credential grant.", false),
                    )
                }
                val credentialReference = CompanionCredentialReference(
                    "desktop:${invitation.desktopId.value}:device:${identity.deviceId.value}",
                )
                val registration = CompanionRegistration(
                    desktopId = invitation.desktopId,
                    desktopDisplayName = invitation.desktopDisplayName,
                    deviceId = identity.deviceId,
                    controlEndpoint = invitation.controlEndpoint,
                    proxyEndpoint = invitation.proxyEndpoint,
                    transportIdentitySha256 = invitation.transportIdentitySha256,
                    rootCertificateSha256 = invitation.rootCertificateSha256,
                    credentialReference = credentialReference,
                    scopes = paired.scopes,
                    pairedAtEpochMillis = pairedAt,
                    credentialExpiresAtEpochMillis = paired.credentialExpiresAtEpochMillis,
                )
                val previousRegistration = registrations.registrations.value.firstOrNull {
                    it.desktopId == invitation.desktopId
                }
                val previousActiveDesktopId = registrations.activeRegistration.value?.desktopId
                var previousCredential: String? = null
                var credentialMutationStarted = false
                try {
                    previousCredential = credentials.read(credentialReference)
                    credentialMutationStarted = true
                    credentials.write(credentialReference, paired.credential)
                    registrations.upsert(registration, makeActive = true)
                    PairCompanionDeviceResult.Paired(registration)
                } catch (cancelled: CancellationException) {
                    rollbackPairing(
                        invitation.desktopId,
                        credentialReference,
                        previousCredential,
                        credentialMutationStarted,
                        previousRegistration,
                        previousActiveDesktopId,
                    )
                    throw cancelled
                } catch (_: Throwable) {
                    rollbackPairing(
                        invitation.desktopId,
                        credentialReference,
                        previousCredential,
                        credentialMutationStarted,
                        previousRegistration,
                        previousActiveDesktopId,
                    )
                    PairCompanionDeviceResult.Rejected(
                        CompanionFailure(CompanionFailureCode.PERSISTENCE_FAILED, "Unable to save paired desktop.", true),
                    )
                }
            }
        }
    }

    private suspend fun rollbackPairing(
        desktopId: CompanionDesktopId,
        credentialReference: CompanionCredentialReference,
        previousCredential: String?,
        credentialMutationStarted: Boolean,
        previousRegistration: CompanionRegistration?,
        previousActiveDesktopId: CompanionDesktopId?,
    ) {
        withContext(NonCancellable) {
            if (credentialMutationStarted) {
                runCatching {
                    if (previousCredential == null) {
                        credentials.remove(credentialReference)
                    } else {
                        credentials.write(credentialReference, previousCredential)
                    }
                }
            }
            runCatching {
                if (previousRegistration == null) {
                    registrations.remove(desktopId)
                } else {
                    registrations.upsert(previousRegistration, makeActive = false)
                }
                registrations.setActive(previousActiveDesktopId)
            }
        }
    }
}

/** Pairing workflow outcome with no credential material. */
public sealed interface PairCompanionDeviceResult {
    public data class Paired(public val registration: CompanionRegistration) : PairCompanionDeviceResult
    public data class Rejected(public val failure: CompanionFailure) : PairCompanionDeviceResult
}

