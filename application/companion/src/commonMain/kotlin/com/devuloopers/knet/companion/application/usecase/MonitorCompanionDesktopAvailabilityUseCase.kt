package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.model.CompanionDesktopAvailability
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/** Lifecycle-owned authenticated desktop availability observation consumed by presentation. */
public interface CompanionDesktopAvailabilityMonitor {
    public val state: StateFlow<CompanionDesktopAvailability>

    /** Observes until the caller cancels its lifecycle scope. */
    public suspend fun execute()
}

/**
 * Continuously proves Home-screen desktop availability without acquiring VPN or proxy resources.
 *
 * The saved endpoint is authenticated first. DNS-SD supplies untrusted recovery candidates only when that endpoint
 * is stale or unavailable. Availability is published only after pinned transport, root-certificate, and credential
 * authentication succeeds.
 */
public class MonitorCompanionDesktopAvailabilityUseCase(
    private val registrations: CompanionRegistrationRepository,
    network: CompanionNetworkObserver,
    private val endpointResolver: CompanionEndpointResolver,
    private val nowEpochMillis: () -> Long,
    private val probeIntervalMillis: Long = DEFAULT_PROBE_INTERVAL_MILLIS,
) : CompanionDesktopAvailabilityMonitor {
    private val networkState: StateFlow<CompanionNetworkState> = network.observe()
    private val mutableState: MutableStateFlow<CompanionDesktopAvailability> =
        MutableStateFlow(CompanionDesktopAvailability.Idle)

    override val state: StateFlow<CompanionDesktopAvailability> = mutableState.asStateFlow()

    init {
        require(probeIntervalMillis in 1_000L..60_000L) { "Desktop availability probe interval is invalid." }
    }

    /** Runs until its owning Home lifecycle is cancelled. Only one caller may own this operation at a time. */
    override suspend fun execute() {
        try {
            while (currentCoroutineContext().isActive) {
                val registration = registrations.activeRegistration.value
                if (registration == null) {
                    mutableState.value = CompanionDesktopAvailability.Idle
                } else if (networkState.value !is CompanionNetworkState.Available) {
                    mutableState.value = CompanionDesktopAvailability.Unavailable(
                        registration.desktopId,
                        networkUnavailable(),
                    )
                } else {
                    verify(registration)
                }
                delay(probeIntervalMillis.milliseconds)
            }
        } finally {
            mutableState.value = CompanionDesktopAvailability.Idle
        }
    }

    private suspend fun verify(registration: CompanionRegistration) {
        if (mutableState.value.desktopIdOrNull() != registration.desktopId) {
            mutableState.value = CompanionDesktopAvailability.Checking(registration.desktopId)
        }
        val recovered = try {
            endpointResolver.resolve(registration)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            CompanionEndpointRecoveryResult.Rejected(desktopUnavailable())
        }
        mutableState.value = when (recovered) {
            is CompanionEndpointRecoveryResult.Recovered -> CompanionDesktopAvailability.Available(
                desktopId = recovered.registration.desktopId,
                verifiedAtEpochMillis = nowEpochMillis(),
            )
            is CompanionEndpointRecoveryResult.Rejected -> recovered.failure.toAvailability(registration)
        }
    }

    private fun CompanionDesktopAvailability.desktopIdOrNull() = when (this) {
        CompanionDesktopAvailability.Idle -> null
        is CompanionDesktopAvailability.Checking -> desktopId
        is CompanionDesktopAvailability.Available -> desktopId
        is CompanionDesktopAvailability.Unavailable -> desktopId
        is CompanionDesktopAvailability.Failed -> desktopId
    }

    private fun CompanionFailure.toAvailability(registration: CompanionRegistration): CompanionDesktopAvailability =
        if (recoverable) {
            CompanionDesktopAvailability.Unavailable(registration.desktopId, this)
        } else {
            CompanionDesktopAvailability.Failed(registration.desktopId, this)
        }

    private fun networkUnavailable(): CompanionFailure = CompanionFailure(
        CompanionFailureCode.NETWORK_UNAVAILABLE,
        "A local network connection is required to reach KNet Desktop.",
        true,
    )

    private fun desktopUnavailable(): CompanionFailure = CompanionFailure(
        CompanionFailureCode.TRANSPORT_UNAVAILABLE,
        "The paired KNet desktop is not currently available on this network.",
        true,
    )

    private companion object {
        const val DEFAULT_PROBE_INTERVAL_MILLIS: Long = 5_000L
    }
}
