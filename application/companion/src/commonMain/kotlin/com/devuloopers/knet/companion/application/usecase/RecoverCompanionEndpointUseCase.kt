package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

/** Authenticates rediscovered addresses and atomically updates the active companion registration. */
public class RecoverCompanionEndpointUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    private val discovery: CompanionDesktopDiscovery,
    private val reconciliation: CompanionEndpointReconciliationClient,
    private val discoveryTimeoutMillis: Long = DEFAULT_DISCOVERY_TIMEOUT_MILLIS,
) {
    init {
        require(discoveryTimeoutMillis in 500L..60_000L) { "Companion discovery timeout is invalid." }
    }

    public suspend fun execute(registration: CompanionRegistration): CompanionEndpointRecoveryResult {
        val credential = readCredential(registration) ?: return credentialUnavailable()

        var lastRejection: CompanionEndpointRecoveryResult.Rejected? = null
        return try {
            discovery.start(setOf(registration.desktopId))
            val recovered = withTimeoutOrNull(discoveryTimeoutMillis.milliseconds) {
                var observedCandidates: List<CompanionDiscoveryCandidate>? = null
                while (currentCoroutineContext().isActive) {
                    when (val discovered = discovery.state.first { state ->
                        state is CompanionDiscoveryState.Failed ||
                                state is CompanionDiscoveryState.Candidates && state.values != observedCandidates
                    }) {
                        is CompanionDiscoveryState.Failed -> {
                            return@withTimeoutOrNull CompanionEndpointRecoveryResult.Rejected(discovered.failure)
                        }

                        is CompanionDiscoveryState.Candidates -> {
                            delay(CANDIDATE_SETTLE_MILLIS.milliseconds)
                            val settledCandidates = (discovery.state.value as? CompanionDiscoveryState.Candidates)
                                ?.values
                                ?: discovered.values
                            observedCandidates = settledCandidates
                            when (val attempt = reconcile(registration, settledCandidates, credential)) {
                                is CompanionEndpointRecoveryResult.Recovered -> return@withTimeoutOrNull attempt
                                is CompanionEndpointRecoveryResult.Rejected -> lastRejection = attempt
                            }
                        }

                        else -> Unit
                    }
                }
                null
            }
            recovered ?: lastRejection ?: CompanionEndpointRecoveryResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
                    "The paired KNet desktop was not found on this network.",
                    true,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            CompanionEndpointRecoveryResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
                    "Unable to start local KNet desktop discovery.",
                    true,
                ),
            )
        } finally {
            runCatching(discovery::stop)
        }
    }

    /** Reconciles an already discovered candidate set without owning the native browse lifecycle. */
    public suspend fun execute(
        registration: CompanionRegistration,
        candidates: List<CompanionDiscoveryCandidate>,
    ): CompanionEndpointRecoveryResult {
        val credential = readCredential(registration) ?: return credentialUnavailable()
        return reconcile(registration, candidates, credential)
    }

    private suspend fun reconcile(
        registration: CompanionRegistration,
        candidates: List<CompanionDiscoveryCandidate>,
        credential: String,
    ): CompanionEndpointRecoveryResult {
        val matchingCandidates = candidates
            .filter { candidate -> candidate.advertisement.matches(setOf(registration.desktopId)) }
        val verified = mutableListOf<VerifiedCandidate>()
        for (candidate in matchingCandidates) {
            for (endpoint in candidate.endpoints) {
                val reconciliationResult = try {
                    reconciliation.reconcile(registration, endpoint, credential)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    continue
                }
                when (reconciliationResult) {
                    is CompanionEndpointReconciliationResult.Rejected -> Unit
                    is CompanionEndpointReconciliationResult.Verified -> {
                        val descriptor = reconciliationResult.descriptor
                        if (
                            descriptor.desktopId == candidate.advertisement.desktopId &&
                            descriptor.runtimeId == candidate.advertisement.runtimeId &&
                            descriptor.accepts(registration.desktopId)
                        ) {
                            verified += VerifiedCandidate(candidate, endpoint, descriptor)
                        }
                    }
                }
            }
        }
        if (verified.isEmpty()) {
            return CompanionEndpointRecoveryResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
                    "No discovered service could prove the paired KNet desktop identity.",
                    false,
                ),
            )
        }
        val identities = verified.map { it.descriptor.desktopId to it.descriptor.runtimeId }.distinct()
        if (identities.size > 1) {
            return CompanionEndpointRecoveryResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.DESKTOP_IDENTITY_CONFLICT,
                    "Multiple running desktops proved the same paired KNet identity.",
                    false,
                ),
            )
        }
        val selected = verified.sortedWith(
            compareBy(
                { it.endpoint.host.contains(':') },
                { it.endpoint.host },
                { it.endpoint.port }
            )
        ).first()
        val updated = registration.copy(
            desktopId = selected.descriptor.desktopId,
            controlEndpoint = CompanionServiceEndpoint(
                selected.endpoint.host,
                selected.descriptor.controlPort,
                secure = true,
            ),
            proxyEndpoint = CompanionServiceEndpoint(
                selected.endpoint.host,
                selected.descriptor.proxyPort,
                secure = true,
            ),
        )
        if (updated == registration) {
            return CompanionEndpointRecoveryResult.Recovered(registration, selected.candidate)
        }
        val migrated = try {
            registrations.migrateIdentity(registration.desktopId, updated, makeActive = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        if (!migrated) {
            return CompanionEndpointRecoveryResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.PERSISTENCE_FAILED,
                    "Unable to save the recovered desktop address.",
                    true
                ),
            )
        }
        return CompanionEndpointRecoveryResult.Recovered(updated, selected.candidate)
    }

    private suspend fun readCredential(registration: CompanionRegistration): String? = try {
        credentials.read(registration.credentialReference)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private fun credentialUnavailable(): CompanionEndpointRecoveryResult.Rejected =
        CompanionEndpointRecoveryResult.Rejected(
            CompanionFailure(CompanionFailureCode.CREDENTIAL_NOT_FOUND, "Paired credential is unavailable.", false),
        )

    private data class VerifiedCandidate(
        val candidate: CompanionDiscoveryCandidate,
        val endpoint: CompanionServiceEndpoint,
        val descriptor: CompanionEndpointDescriptor,
    )

    private companion object {
        const val DEFAULT_DISCOVERY_TIMEOUT_MILLIS: Long = 8_000L
        const val CANDIDATE_SETTLE_MILLIS: Long = 750L
    }
}
