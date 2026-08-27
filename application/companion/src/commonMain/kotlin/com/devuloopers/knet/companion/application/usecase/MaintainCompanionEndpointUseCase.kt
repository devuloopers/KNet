package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDiscoveryCandidate
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps an active inspection attached to the paired desktop as its LAN address changes.
 *
 * DNS-SD remains only a candidate source. Every changed address is accepted by [RecoverCompanionEndpointUseCase]
 * only after the existing pinned root, transport identity, and credential authenticate the desktop.
 */
public class MaintainCompanionEndpointUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val discovery: CompanionDesktopDiscovery,
    private val recoverEndpoint: RecoverCompanionEndpointUseCase,
    private val transport: CompanionTransport,
    private val reconnect: ConnectCompanionUseCase,
) {
    public suspend fun execute() {
        var browsingFor: CompanionDesktopId? = null
        var observedCandidates: List<CompanionDiscoveryCandidate>? = null
        try {
            while (currentCoroutineContext().isActive) {
                val registration = registrations.activeRegistration.value
                if (registration == null) {
                    if (browsingFor != null) discovery.stop()
                    browsingFor = null
                    observedCandidates = null
                    delay(REGISTRATION_POLL_MILLIS.milliseconds)
                    continue
                }
                if (browsingFor != registration.desktopId) {
                    val started = runCatching {
                        discovery.start(setOf(registration.desktopId))
                    }.isSuccess
                    if (!started) {
                        browsingFor = null
                        observedCandidates = null
                        delay(DISCOVERY_RETRY_MILLIS.milliseconds)
                        continue
                    }
                    browsingFor = registration.desktopId
                    observedCandidates = null
                }
                val discovered = withTimeoutOrNull(REGISTRATION_POLL_MILLIS.milliseconds) {
                    discovery.state.first { state ->
                        state is CompanionDiscoveryState.Failed ||
                            state is CompanionDiscoveryState.Candidates && state.values != observedCandidates
                    }
                } ?: continue
                if (discovered is CompanionDiscoveryState.Failed) {
                    discovery.stop()
                    browsingFor = null
                    observedCandidates = null
                    delay(DISCOVERY_RETRY_MILLIS.milliseconds)
                    continue
                }
                discovered as CompanionDiscoveryState.Candidates
                delay(CANDIDATE_SETTLE_MILLIS.milliseconds)
                val settledCandidates = (discovery.state.value as? CompanionDiscoveryState.Candidates)
                    ?.values
                    ?: discovered.values
                observedCandidates = settledCandidates
                val before = registration.controlEndpoint to registration.proxyEndpoint
                val wasConnected = transport.state.value is CompanionConnectionState.Connected
                val result = recoverEndpoint.execute(registration, settledCandidates)
                if (result is CompanionEndpointRecoveryResult.Recovered) {
                    val after = result.registration.controlEndpoint to result.registration.proxyEndpoint
                    if (wasConnected && before != after) reconnect.execute()
                }
            }
        } finally {
            runCatching(discovery::stop)
        }
    }

    private companion object {
        const val REGISTRATION_POLL_MILLIS: Long = 500L
        const val CANDIDATE_SETTLE_MILLIS: Long = 750L
        const val DISCOVERY_RETRY_MILLIS: Long = 2_000L
    }
}
