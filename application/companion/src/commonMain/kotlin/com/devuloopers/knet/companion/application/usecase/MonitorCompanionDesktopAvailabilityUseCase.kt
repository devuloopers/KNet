package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
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
 * The saved pinned endpoint is tried first. DNS-SD is used only as an untrusted recovery source when that endpoint
 * cannot be authenticated, keeping steady-state network and battery cost small.
 */
public class MonitorCompanionDesktopAvailabilityUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    network: CompanionNetworkObserver,
    private val reconciliation: CompanionEndpointReconciliationClient,
    private val recoverEndpoint: RecoverCompanionEndpointUseCase,
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
        val credential = readCredential(registration)
        if (credential == null) {
            mutableState.value = CompanionDesktopAvailability.Failed(
                registration.desktopId,
                credentialUnavailable(),
            )
            return
        }
        val direct = try {
            reconciliation.reconcile(registration, registration.controlEndpoint, credential)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            CompanionEndpointReconciliationResult.Rejected(desktopUnavailable())
        }
        if (direct is CompanionEndpointReconciliationResult.Verified) {
            mutableState.value = CompanionDesktopAvailability.Available(
                desktopId = direct.descriptor.desktopId,
                verifiedAtEpochMillis = nowEpochMillis(),
            )
            return
        }

        val recovered = try {
            recoverEndpoint.execute(registration)
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

    private suspend fun readCredential(registration: CompanionRegistration): String? = try {
        credentials.read(registration.credentialReference)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
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

    private fun credentialUnavailable(): CompanionFailure = CompanionFailure(
        CompanionFailureCode.CREDENTIAL_NOT_FOUND,
        "The paired desktop credential is unavailable.",
        false,
    )

    private companion object {
        const val DEFAULT_PROBE_INTERVAL_MILLIS: Long = 5_000L
    }
}
