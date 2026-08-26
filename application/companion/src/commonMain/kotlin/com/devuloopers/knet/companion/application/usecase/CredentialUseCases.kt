package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCredentialRefreshResult
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Rotates a credential without changing device or desktop identity. */
public class RefreshCompanionCredentialUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    private val pairingClient: CompanionPairingClient,
    private val nowEpochMillis: () -> Long,
) {
    public suspend fun execute(): RefreshCompanionCredentialResult {
        val registration = registrations.activeRegistration.value
            ?: return RefreshCompanionCredentialResult.Rejected(registrationMissing())
        val current = try {
            credentials.read(registration.credentialReference)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return RefreshCompanionCredentialResult.Rejected(
                CompanionFailure(CompanionFailureCode.PERSISTENCE_FAILED, "Unable to read the paired credential.", true),
            )
        }
            ?: return RefreshCompanionCredentialResult.Rejected(
                CompanionFailure(CompanionFailureCode.CREDENTIAL_NOT_FOUND, "Paired credential is unavailable.", false),
            )
        val result = try {
            pairingClient.refresh(registration, current)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return RefreshCompanionCredentialResult.Rejected(
                CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Unable to refresh the paired credential.", true),
            )
        }
        return when (result) {
            is CompanionCredentialRefreshResult.Rejected -> RefreshCompanionCredentialResult.Rejected(result.failure)
            is CompanionCredentialRefreshResult.Refreshed -> {
                if (result.credential.isBlank() || result.credentialExpiresAtEpochMillis <= nowEpochMillis()) {
                    return RefreshCompanionCredentialResult.Rejected(
                        CompanionFailure(CompanionFailureCode.PAIRING_REJECTED, "Desktop returned an invalid refreshed credential.", false),
                    )
                }
                val updated = registration.copy(credentialExpiresAtEpochMillis = result.credentialExpiresAtEpochMillis)
                var refreshedCredentialPersisted = false
                try {
                    withContext(NonCancellable) {
                        credentials.write(registration.credentialReference, result.credential)
                        refreshedCredentialPersisted = true
                        registrations.upsert(updated, makeActive = true)
                    }
                    RefreshCompanionCredentialResult.Refreshed(updated)
                } catch (cancelled: CancellationException) {
                    discardInvalidCredentialUnlessRefreshedWasPersisted(
                        registration,
                        refreshedCredentialPersisted,
                    )
                    throw cancelled
                } catch (_: Throwable) {
                    discardInvalidCredentialUnlessRefreshedWasPersisted(
                        registration,
                        refreshedCredentialPersisted,
                    )
                    RefreshCompanionCredentialResult.Rejected(
                        CompanionFailure(CompanionFailureCode.PERSISTENCE_FAILED, "Unable to save refreshed credential.", true),
                    )
                }
            }
        }
    }

    private suspend fun discardInvalidCredentialUnlessRefreshedWasPersisted(
        registration: CompanionRegistration,
        refreshedCredentialPersisted: Boolean,
    ) {
        if (refreshedCredentialPersisted) return
        withContext(NonCancellable) {
            // The desktop has already rotated the credential, so the previous local secret is no
            // longer valid and must not be restored after a protected-storage failure.
            runCatching { credentials.remove(registration.credentialReference) }
        }
    }
}

/** Credential refresh outcome with no secret material. */
public sealed interface RefreshCompanionCredentialResult {
    public data class Refreshed(public val registration: CompanionRegistration) : RefreshCompanionCredentialResult
    public data class Rejected(public val failure: CompanionFailure) : RefreshCompanionCredentialResult
}
