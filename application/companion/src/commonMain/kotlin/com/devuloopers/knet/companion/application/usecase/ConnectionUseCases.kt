package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointResolver
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionNetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionSessionObservation
import com.devuloopers.knet.companion.application.contract.CompanionSessionPhase
import com.devuloopers.knet.companion.model.CompanionInspectionState

/** Connects the authenticated transport using a credential read only at the adapter boundary. */
public class ConnectCompanionUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    private val network: CompanionNetworkObserver,
    private val transport: CompanionTransport,
    private val nowEpochMillis: () -> Long,
    private val endpointResolver: CompanionEndpointResolver,
) {
    public suspend fun execute(): ConnectCompanionResult {
        val registration = registrations.activeRegistration.value
            ?: return ConnectCompanionResult.Rejected(registrationMissing())
        connectionPrerequisiteFailure(registration)?.let { failure ->
            return ConnectCompanionResult.Rejected(failure)
        }
        return when (val resolution = endpointResolver.resolve(registration)) {
            is CompanionEndpointRecoveryResult.Rejected -> ConnectCompanionResult.Rejected(resolution.failure)
            is CompanionEndpointRecoveryResult.Recovered -> connect(resolution)
        }
    }

    /** Reconnects with an endpoint that has already passed authenticated reconciliation. */
    public suspend fun connect(
        resolution: CompanionEndpointRecoveryResult.Recovered,
    ): ConnectCompanionResult = connect(resolution.registration)

    private suspend fun connect(registration: com.devuloopers.knet.companion.model.CompanionRegistration): ConnectCompanionResult {
        val activeRegistration = registrations.activeRegistration.value
            ?: return ConnectCompanionResult.Rejected(registrationMissing())
        if (activeRegistration.desktopId != registration.desktopId) {
            return ConnectCompanionResult.Rejected(
                CompanionFailure(
                    CompanionFailureCode.REGISTRATION_NOT_FOUND,
                    "The discovered desktop is no longer the active registration.",
                    true,
                ),
            )
        }
        connectionPrerequisiteFailure(registration)?.let { failure ->
            return ConnectCompanionResult.Rejected(failure)
        }
        val credential = try {
            credentials.read(registration.credentialReference)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return ConnectCompanionResult.Rejected(
                CompanionFailure(CompanionFailureCode.PERSISTENCE_FAILED, "Unable to read the paired credential.", true),
            )
        } ?: return ConnectCompanionResult.Rejected(
                CompanionFailure(CompanionFailureCode.CREDENTIAL_NOT_FOUND, "Paired credential is unavailable.", false),
            )
        return connectTransport(registration, credential)
    }

    private fun connectionPrerequisiteFailure(
        registration: com.devuloopers.knet.companion.model.CompanionRegistration,
    ): CompanionFailure? {
        if (nowEpochMillis() >= registration.credentialExpiresAtEpochMillis) {
            return CompanionFailure(CompanionFailureCode.CREDENTIAL_EXPIRED, "Paired credential expired.", true)
        }
        val networkState = try {
            network.observe().value
        } catch (_: Throwable) {
            CompanionNetworkState.Unavailable
        }
        if (networkState !is CompanionNetworkState.Available) {
            return CompanionFailure(CompanionFailureCode.NETWORK_UNAVAILABLE, "A network connection is required.", true)
        }
        return null
    }

    private suspend fun connectTransport(
        registration: com.devuloopers.knet.companion.model.CompanionRegistration,
        credential: String,
    ): ConnectCompanionResult = try {
            when (val result = transport.connect(registration, credential)) {
                CompanionTransportResult.Connected -> ConnectCompanionResult.Connected
                is CompanionTransportResult.Rejected -> ConnectCompanionResult.Rejected(result.failure)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ConnectCompanionResult.Rejected(
                CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "Unable to reach the paired desktop securely.", true),
            )
        }
}

/** Connection workflow outcome. */
public sealed interface ConnectCompanionResult {
    public data object Connected : ConnectCompanionResult
    public data class Rejected(public val failure: CompanionFailure) : ConnectCompanionResult
}

/** Disconnects the data plane without deleting pairing or certificate state. */
public class DisconnectCompanionUseCase(
    private val transport: CompanionTransport,
) {
    public suspend fun execute(): Unit = transport.disconnect()
}

/** Provides connection state without exposing a concrete carrier. */
public class ObserveCompanionConnectionUseCase(
    private val transport: CompanionTransport,
    private val session: StateFlow<CompanionSessionObservation?>,
    private val inspection: CompanionInspectionController,
) {
    /** Synchronous initial projection; collecting [state] owns no additional background scope. */
    public val currentState: CompanionConnectionState
        get() = project(transport.state.value, session.value, inspection.state.value)

    /** Control health overrides the initial proxy-readiness probe only while its matching VPN is running. */
    public val state: Flow<CompanionConnectionState> = combine(transport.state, session, inspection.state, ::project)
        .distinctUntilChanged()

    private fun project(carrier: CompanionConnectionState, observation: CompanionSessionObservation?,
        capture: CompanionInspectionState): CompanionConnectionState {
        if (capture !is CompanionInspectionState.Running || carrier !is CompanionConnectionState.Connected ||
            observation?.desktopId != carrier.desktopId) return carrier
        return when (observation.phase) {
            CompanionSessionPhase.CONNECTED -> carrier
            CompanionSessionPhase.CONNECTING, CompanionSessionPhase.RECONNECTING ->
                CompanionConnectionState.Reconnecting(carrier.desktopId, attempt = 1)
            CompanionSessionPhase.REJECTED -> CompanionConnectionState.Failed(CompanionFailure(
                CompanionFailureCode.PAIRING_REJECTED, "The desktop no longer accepts this VPN session. Reconnect after checking pairing.", false))
        }
    }
}

/** Provides platform network state without exposing ConnectivityManager or Network.framework. */
public class ObserveCompanionNetworkUseCase(
    observer: CompanionNetworkObserver,
) {
    public val state: StateFlow<CompanionNetworkState> = observer.observe()
}

/** Restores only the authenticated connection; native capture requires an explicit product decision. */
public class RecoverCompanionSessionUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val connect: ConnectCompanionUseCase,
) {
    public suspend fun execute(): ConnectCompanionResult =
        if (registrations.activeRegistration.value == null) {
            ConnectCompanionResult.Rejected(registrationMissing())
        } else {
            connect.execute()
        }
}
