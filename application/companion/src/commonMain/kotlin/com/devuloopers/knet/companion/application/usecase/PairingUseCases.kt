package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolutionResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
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
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Validates a scanned or pasted invitation without retaining its secret. */
public class AcceptPairingInvitationUseCase(
    private val codec: CompanionInvitationCodec,
    private val resolver: CompanionInvitationResolver,
    private val nowEpochMillis: () -> Long,
) {
    /** Decodes and redeems [payload] without retaining its bootstrap secret after completion. */
    public suspend fun execute(payload: String): AcceptPairingInvitationResult {
        if (payload.isBlank()) return AcceptPairingInvitationResult.Rejected(
            CompanionFailure(CompanionFailureCode.INVITATION_INVALID, "Pairing invitation is empty.", false),
        )
        return when (val decoded = codec.decode(payload.trim())) {
            is InvitationDecodeResult.Rejected -> AcceptPairingInvitationResult.Rejected(decoded.failure)
            is InvitationDecodeResult.Accepted -> {
                if (nowEpochMillis() >= decoded.bootstrap.expiresAtEpochMillis) {
                    AcceptPairingInvitationResult.Rejected(
                        CompanionFailure(CompanionFailureCode.INVITATION_EXPIRED, "Pairing invitation expired.", false),
                    )
                } else {
                    resolve(decoded.bootstrap)
                }
            }
        }
    }

    private suspend fun resolve(
        bootstrap: CompanionPairingBootstrap,
    ): AcceptPairingInvitationResult = when (val result = resolver.resolve(bootstrap)) {
        is CompanionInvitationResolutionResult.Rejected -> AcceptPairingInvitationResult.Rejected(result.failure)
        is CompanionInvitationResolutionResult.Resolved -> {
            val invitation = result.invitation
            if (
                nowEpochMillis() >= invitation.pairing.expiresAtEpochMillis ||
                invitation.pairing.expiresAtEpochMillis != bootstrap.expiresAtEpochMillis ||
                invitation.controlEndpoint != bootstrap.retrievalEndpoint ||
                invitation.transportIdentitySha256 != bootstrap.transportIdentitySha256 ||
                invitation.rootCertificateSha256 != bootstrap.rootCertificateSha256
            ) {
                AcceptPairingInvitationResult.Rejected(
                    CompanionFailure(
                        CompanionFailureCode.INVITATION_INVALID,
                        "Desktop returned pairing details that do not match the scanned invitation.",
                        false,
                    ),
                )
            } else {
                AcceptPairingInvitationResult.Accepted(invitation)
            }
        }
    }
}

/** In-memory invitation validation outcome. */
public sealed interface AcceptPairingInvitationResult {
    public data class Accepted(public val invitation: CompanionPairingInvitation) : AcceptPairingInvitationResult
    public data class Rejected(public val failure: CompanionFailure) : AcceptPairingInvitationResult
}

/** Completes pairing and atomically commits the secret plus its non-secret registration locally. */
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
                    rootCertificate = invitation.rootCertificate,
                    credentialReference = credentialReference,
                    scopes = paired.scopes,
                    pairedAtEpochMillis = pairedAt,
                    credentialExpiresAtEpochMillis = paired.credentialExpiresAtEpochMillis,
                )
                val previousRegistration = registrations.registrations.value.firstOrNull {
                    it.desktopId == invitation.desktopId
                }
                val previousActiveDesktopId = registrations.activeRegistration.value?.desktopId
                try {
                    withContext(NonCancellable) {
                        credentials.write(credentialReference, paired.credential)
                        registrations.upsert(registration, makeActive = true)
                    }
                    PairCompanionDeviceResult.Paired(registration)
                } catch (cancelled: CancellationException) {
                    invalidateLocalPairing(
                        invitation.desktopId,
                        credentialReference,
                        previousRegistration?.credentialReference,
                        previousActiveDesktopId,
                    )
                    throw cancelled
                } catch (_: Throwable) {
                    invalidateLocalPairing(
                        invitation.desktopId,
                        credentialReference,
                        previousRegistration?.credentialReference,
                        previousActiveDesktopId,
                    )
                    PairCompanionDeviceResult.Rejected(
                        CompanionFailure(CompanionFailureCode.PERSISTENCE_FAILED, "Unable to save paired desktop.", true),
                    )
                }
            }
        }
    }

    private suspend fun invalidateLocalPairing(
        desktopId: CompanionDesktopId,
        credentialReference: CompanionCredentialReference,
        previousCredentialReference: CompanionCredentialReference?,
        previousActiveDesktopId: CompanionDesktopId?,
    ) {
        withContext(NonCancellable) {
            // A successful remote pairing invalidates any prior credential for this desktop. Never
            // restore that stale secret when the local commit fails; require a fresh pairing instead.
            listOfNotNull(credentialReference, previousCredentialReference).distinct().forEach { reference ->
                runCatching { credentials.remove(reference) }
            }
            runCatching { registrations.remove(desktopId) }
            runCatching { registrations.setActive(previousActiveDesktopId?.takeUnless { it == desktopId }) }
        }
    }
}

/** Pairing workflow outcome with no credential material. */
public sealed interface PairCompanionDeviceResult {
    public data class Paired(public val registration: CompanionRegistration) : PairCompanionDeviceResult
    public data class Rejected(public val failure: CompanionFailure) : PairCompanionDeviceResult
}
