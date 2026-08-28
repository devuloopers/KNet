package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointResolver
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.model.CompanionDiscoveryCandidate
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import com.devuloopers.knet.companion.model.CompanionEndpointDescriptor
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.core.logger.KNetLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

/** Authenticates the saved endpoint first, then discovers and persists a replacement when recovery is required. */
public class RecoverCompanionEndpointUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    private val discovery: CompanionDesktopDiscovery,
    private val reconciliation: CompanionEndpointReconciliationClient,
    private val discoveryTimeoutMillis: Long = DEFAULT_DISCOVERY_TIMEOUT_MILLIS,
) : CompanionEndpointResolver {
    init {
        require(discoveryTimeoutMillis in 500L..60_000L) { "Companion discovery timeout is invalid." }
    }

    override suspend fun resolve(registration: CompanionRegistration): CompanionEndpointRecoveryResult {
        KNetLogger.debug(DISCOVERY_TAG) {
            "companion_event=endpoint_recovery_started desktop_id=${registration.desktopId.value}"
        }
        val credential = readCredential(registration)
        if (credential == null) {
            KNetLogger.warn(DISCOVERY_TAG) {
                "companion_event=endpoint_recovery_rejected desktop_id=${registration.desktopId.value} " +
                    "reason=credential_unavailable"
            }
            return credentialUnavailable()
        }

        when (val direct = reconcilePersistedEndpoint(registration, credential)) {
            is CompanionEndpointRecoveryResult.Recovered -> {
                KNetLogger.debug(DISCOVERY_TAG) {
                    "companion_event=endpoint_recovery_completed " +
                        "desktop_id=${direct.registration.desktopId.value} source=persisted " +
                        "endpoint=${direct.registration.controlEndpoint.host}:" +
                        direct.registration.controlEndpoint.port
                }
                return direct
            }
            is CompanionEndpointRecoveryResult.Rejected -> {
                if (direct.failure.code == CompanionFailureCode.PERSISTENCE_FAILED) return direct
                KNetLogger.debug(DISCOVERY_TAG) {
                    "companion_event=endpoint_recovery_fallback desktop_id=${registration.desktopId.value} " +
                        "reason=${direct.failure.code}"
                }
            }
        }

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
                            KNetLogger.warn(DISCOVERY_TAG) {
                                "companion_event=endpoint_recovery_rejected desktop_id=${registration.desktopId.value} " +
                                    "reason=discovery_failed code=${discovered.failure.code}"
                            }
                            return@withTimeoutOrNull CompanionEndpointRecoveryResult.Rejected(discovered.failure)
                        }

                        is CompanionDiscoveryState.Candidates -> {
                            delay(CANDIDATE_SETTLE_MILLIS.milliseconds)
                            val settledCandidates = (discovery.state.value as? CompanionDiscoveryState.Candidates)
                                ?.values
                                ?: discovered.values
                            observedCandidates = settledCandidates
                            KNetLogger.debug(DISCOVERY_TAG) {
                                "companion_event=endpoint_recovery_candidates desktop_id=${registration.desktopId.value} " +
                                    "candidate_count=${settledCandidates.size} " +
                                    "endpoint_count=${settledCandidates.sumOf { it.endpoints.size }}"
                            }
                            when (val attempt = reconcile(registration, settledCandidates, credential)) {
                                is CompanionEndpointRecoveryResult.Recovered -> {
                                    KNetLogger.info(DISCOVERY_TAG) {
                                        "companion_event=endpoint_recovery_completed " +
                                            "desktop_id=${attempt.registration.desktopId.value} " +
                                            "endpoint=${attempt.registration.controlEndpoint.host}:" +
                                            attempt.registration.controlEndpoint.port
                                    }
                                    return@withTimeoutOrNull attempt
                                }
                                is CompanionEndpointRecoveryResult.Rejected -> {
                                    KNetLogger.warn(DISCOVERY_TAG) {
                                        "companion_event=endpoint_recovery_candidate_rejected " +
                                            "desktop_id=${registration.desktopId.value} code=${attempt.failure.code}"
                                    }
                                    lastRejection = attempt
                                }
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
            ).also {
                KNetLogger.warn(DISCOVERY_TAG) {
                    "companion_event=endpoint_recovery_timeout desktop_id=${registration.desktopId.value} " +
                        "timeout_ms=$discoveryTimeoutMillis"
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            KNetLogger.error(DISCOVERY_TAG, failure) {
                "companion_event=endpoint_recovery_failed desktop_id=${registration.desktopId.value} " +
                    "reason=${failure::class.simpleName ?: "unknown"}"
            }
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

    /** Authenticates the saved endpoint before paying the cost of native service discovery. */
    private suspend fun reconcilePersistedEndpoint(
        registration: CompanionRegistration,
        credential: String,
    ): CompanionEndpointRecoveryResult {
        val endpoint = registration.controlEndpoint
        KNetLogger.debug(DISCOVERY_TAG) {
            "companion_event=endpoint_reconciliation_started desktop_id=${registration.desktopId.value} " +
                "source=persisted endpoint=${endpoint.host}:${endpoint.port}"
        }
        val result = try {
            reconciliation.reconcile(registration, endpoint, credential)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            KNetLogger.debug(DISCOVERY_TAG) {
                "companion_event=endpoint_reconciliation_failed desktop_id=${registration.desktopId.value} " +
                    "source=persisted reason=${failure::class.simpleName ?: "unknown"}"
            }
            return transportUnavailable()
        }
        return when (result) {
            is CompanionEndpointReconciliationResult.Rejected ->
                CompanionEndpointRecoveryResult.Rejected(result.failure)
            is CompanionEndpointReconciliationResult.Verified -> {
                if (!result.descriptor.accepts(registration.desktopId)) {
                    identityMismatch()
                } else {
                    recoverVerifiedEndpoint(registration, endpoint, result.descriptor)
                }
            }
        }
    }

    private suspend fun reconcile(
        registration: CompanionRegistration,
        candidates: List<CompanionDiscoveryCandidate>,
        credential: String,
    ): CompanionEndpointRecoveryResult {
        val matchingCandidates = candidates
            .filter { candidate -> candidate.advertisement.matches(setOf(registration.desktopId)) }
        if (matchingCandidates.isEmpty()) {
            KNetLogger.warn(DISCOVERY_TAG) {
                "companion_event=endpoint_reconciliation_skipped desktop_id=${registration.desktopId.value} " +
                    "reason=no_matching_candidate candidate_count=${candidates.size}"
            }
            return identityMismatch()
        }
        val verified = mutableListOf<VerifiedCandidate>()
        var preferredRejection: CompanionEndpointRecoveryResult.Rejected? = null
        for (candidate in matchingCandidates) {
            for (endpoint in candidate.endpoints) {
                KNetLogger.debug(DISCOVERY_TAG) {
                    "companion_event=endpoint_reconciliation_started desktop_id=${registration.desktopId.value} " +
                        "candidate=${candidate.instanceName} endpoint=${endpoint.host}:${endpoint.port}"
                }
                val reconciliationResult = try {
                    reconciliation.reconcile(registration, endpoint, credential)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    KNetLogger.warn(DISCOVERY_TAG) {
                        "companion_event=endpoint_reconciliation_failed desktop_id=${registration.desktopId.value} " +
                            "endpoint=${endpoint.host}:${endpoint.port} " +
                            "reason=${failure::class.simpleName ?: "unknown"}"
                    }
                    preferredRejection = preferRejection(preferredRejection, transportUnavailable())
                    continue
                }
                when (reconciliationResult) {
                    is CompanionEndpointReconciliationResult.Rejected -> {
                        KNetLogger.warn(DISCOVERY_TAG) {
                            "companion_event=endpoint_reconciliation_rejected desktop_id=${registration.desktopId.value} " +
                                "endpoint=${endpoint.host}:${endpoint.port} code=${reconciliationResult.failure.code}"
                        }
                        preferredRejection = preferRejection(
                            preferredRejection,
                            CompanionEndpointRecoveryResult.Rejected(reconciliationResult.failure),
                        )
                    }
                    is CompanionEndpointReconciliationResult.Verified -> {
                        val descriptor = reconciliationResult.descriptor
                        if (
                            descriptor.desktopId == candidate.advertisement.desktopId &&
                            descriptor.runtimeId == candidate.advertisement.runtimeId &&
                            descriptor.accepts(registration.desktopId)
                        ) {
                            verified += VerifiedCandidate(endpoint, descriptor)
                            KNetLogger.debug(DISCOVERY_TAG) {
                                "companion_event=endpoint_reconciliation_verified " +
                                    "desktop_id=${descriptor.desktopId.value} runtime_id=${descriptor.runtimeId.value} " +
                                    "endpoint=${endpoint.host}:${endpoint.port}"
                            }
                        } else {
                            KNetLogger.warn(DISCOVERY_TAG) {
                                "companion_event=endpoint_reconciliation_rejected " +
                                    "desktop_id=${registration.desktopId.value} endpoint=${endpoint.host}:${endpoint.port} " +
                                    "reason=descriptor_identity"
                            }
                            preferredRejection = preferRejection(preferredRejection, identityMismatch())
                        }
                    }
                }
            }
        }
        if (verified.isEmpty()) {
            return preferredRejection ?: identityMismatch()
        }
        val identities = verified.map { it.descriptor.desktopId to it.descriptor.runtimeId }.distinct()
        if (identities.size > 1) {
            KNetLogger.error(DISCOVERY_TAG) {
                "companion_event=endpoint_recovery_conflict desktop_id=${registration.desktopId.value} " +
                    "verified_identity_count=${identities.size}"
            }
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
        return recoverVerifiedEndpoint(registration, selected.endpoint, selected.descriptor)
    }

    private suspend fun recoverVerifiedEndpoint(
        registration: CompanionRegistration,
        endpoint: CompanionServiceEndpoint,
        descriptor: CompanionEndpointDescriptor,
    ): CompanionEndpointRecoveryResult {
        val updated = registration.copy(
            desktopId = descriptor.desktopId,
            controlEndpoint = CompanionServiceEndpoint(
                endpoint.host,
                descriptor.controlPort,
                scheme = CompanionEndpointScheme.HTTPS,
            ),
            proxyEndpoint = CompanionServiceEndpoint(
                endpoint.host,
                descriptor.proxyPort,
                scheme = CompanionEndpointScheme.HTTPS,
            ),
        )
        if (updated == registration) {
            return CompanionEndpointRecoveryResult.Recovered(registration)
        }
        val migrated = try {
            registrations.migrateIdentity(registration.desktopId, updated, makeActive = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        if (!migrated) {
            KNetLogger.warn(DISCOVERY_TAG) {
                "companion_event=endpoint_recovery_rejected desktop_id=${registration.desktopId.value} " +
                    "reason=persistence"
            }
            return CompanionEndpointRecoveryResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.PERSISTENCE_FAILED,
                    "Unable to save the recovered desktop address.",
                    true
                ),
            )
        }
        return CompanionEndpointRecoveryResult.Recovered(updated)
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

    private fun identityMismatch(): CompanionEndpointRecoveryResult.Rejected = CompanionEndpointRecoveryResult.Rejected(
        CompanionFailure(
            CompanionFailureCode.TRANSPORT_IDENTITY_MISMATCH,
            "No discovered service could prove the paired KNet desktop identity.",
            false,
        ),
    )

    private fun transportUnavailable(): CompanionEndpointRecoveryResult.Rejected = CompanionEndpointRecoveryResult.Rejected(
        CompanionFailure(
            CompanionFailureCode.TRANSPORT_UNAVAILABLE,
            "Unable to verify the KNet desktop endpoint.",
            true,
        ),
    )

    /** Non-recoverable authentication failures take precedence over transient endpoint failures. */
    private fun preferRejection(
        current: CompanionEndpointRecoveryResult.Rejected?,
        candidate: CompanionEndpointRecoveryResult.Rejected,
    ): CompanionEndpointRecoveryResult.Rejected = when {
        current == null -> candidate
        current.failure.recoverable && !candidate.failure.recoverable -> candidate
        else -> current
    }

    private data class VerifiedCandidate(
        val endpoint: CompanionServiceEndpoint,
        val descriptor: CompanionEndpointDescriptor,
    )

    private companion object {
        const val DEFAULT_DISCOVERY_TIMEOUT_MILLIS: Long = 8_000L
        const val CANDIDATE_SETTLE_MILLIS: Long = 750L
        const val DISCOVERY_TAG: String = "CompanionDiscovery"
    }
}
