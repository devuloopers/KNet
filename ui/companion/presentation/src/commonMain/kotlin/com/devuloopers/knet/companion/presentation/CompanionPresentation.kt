package com.devuloopers.knet.companion.presentation

import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationResult
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationUseCase
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionResult
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DisconnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateResult
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateUseCase
import com.devuloopers.knet.companion.application.usecase.ForgetCompanionDesktopUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionConnectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionNetworkUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionRegistrationsUseCase
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceResult
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceUseCase
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialResult
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialUseCase
import com.devuloopers.knet.companion.application.usecase.SelectCompanionRegistrationUseCase
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionResult
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.StopCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.VerifyCompanionCertificateTrustUseCase
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable companion presentation state rendered by shared Compose Multiplatform UI. */
public data class CompanionUiState(
    public val registrations: List<CompanionRegistration> = emptyList(),
    public val activeRegistration: CompanionRegistration? = null,
    public val invitationDesktopName: String? = null,
    public val pairingInProgress: Boolean = false,
    public val connection: CompanionConnectionState = CompanionConnectionState.Disconnected,
    public val inspection: CompanionInspectionState = CompanionInspectionState.Stopped,
    public val certificate: CompanionCertificateState = CompanionCertificateState.Unknown,
    public val network: CompanionNetworkState = CompanionNetworkState.Unknown,
    public val operationInProgress: Boolean = false,
    public val failure: CompanionFailure? = null,
)

/** User intents accepted by the shared companion ViewModel. */
public sealed interface CompanionAction {
    public data class InvitationSubmitted(public val payload: String) : CompanionAction
    public data class PairSubmitted(public val deviceDisplayName: String) : CompanionAction
    public data class RegistrationSelected(public val desktopId: CompanionDesktopId) : CompanionAction
    public data object ConnectRequested : CompanionAction
    public data object DisconnectRequested : CompanionAction
    public data object StartInspectionRequested : CompanionAction
    public data class VpnConsentResolved(public val granted: Boolean) : CompanionAction
    public data object StopInspectionRequested : CompanionAction
    public data object DownloadCertificateRequested : CompanionAction
    public data object VerifyCertificateTrustRequested : CompanionAction
    public data object OpenCertificateTrustSettingsRequested : CompanionAction
    public data object RefreshCredentialRequested : CompanionAction
    public data class ForgetDesktopRequested(public val desktopId: CompanionDesktopId) : CompanionAction
    public data object ClearFailure : CompanionAction
}

/** One-shot native work. Effects contain portable values and never Android Intent or Apple framework types. */
public sealed interface CompanionEffect {
    public data object RequestVpnConsent : CompanionEffect
    public data class InstallCertificate(public val artifact: CompanionCertificateArtifact) : CompanionEffect
    public data object OpenCertificateTrustSettings : CompanionEffect
}

/** Use-case bundle that keeps ViewModel construction readable without introducing a service locator. */
public data class CompanionViewModelUseCases(
    public val acceptInvitation: AcceptPairingInvitationUseCase,
    public val pair: PairCompanionDeviceUseCase,
    public val observeRegistrations: ObserveCompanionRegistrationsUseCase,
    public val selectRegistration: SelectCompanionRegistrationUseCase,
    public val connect: ConnectCompanionUseCase,
    public val disconnect: DisconnectCompanionUseCase,
    public val observeConnection: ObserveCompanionConnectionUseCase,
    public val observeNetwork: ObserveCompanionNetworkUseCase,
    public val startInspection: StartCompanionInspectionUseCase,
    public val stopInspection: StopCompanionInspectionUseCase,
    public val observeInspection: ObserveCompanionInspectionUseCase,
    public val downloadCertificate: DownloadCompanionRootCertificateUseCase,
    public val verifyCertificateTrust: VerifyCompanionCertificateTrustUseCase,
    public val refreshCredential: RefreshCompanionCredentialUseCase,
    public val forgetDesktop: ForgetCompanionDesktopUseCase,
)

/**
 * Framework-neutral shared ViewModel. A product supplies a lifecycle-owned [CoroutineScope] and invokes
 * [close] when that lifecycle ends; this class never creates a process-global scope.
 */
public class CompanionViewModel(
    private val useCases: CompanionViewModelUseCases,
    parentScope: CoroutineScope,
) {
    private val viewModelJob: Job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope: CoroutineScope = CoroutineScope(parentScope.coroutineContext + viewModelJob)
    private val mutableState: MutableStateFlow<CompanionUiState> = MutableStateFlow(CompanionUiState())
    private val activeOperationCount: MutableStateFlow<Int> = MutableStateFlow(0)
    private val effectChannel: Channel<CompanionEffect> = Channel(capacity = Channel.BUFFERED)
    private var pendingInvitation: CompanionPairingInvitation? = null
    private var closed: Boolean = false

    public val state: StateFlow<CompanionUiState> = mutableState.asStateFlow()
    public val effects: Flow<CompanionEffect> = effectChannel.receiveAsFlow()

    init {
        scope.launch {
            useCases.observeRegistrations.registrations.collect { registrations ->
                mutableState.update { it.copy(registrations = registrations) }
            }
        }
        scope.launch {
            useCases.observeRegistrations.activeRegistration.collect { registration ->
                mutableState.update { it.copy(activeRegistration = registration, certificate = CompanionCertificateState.Unknown) }
                if (registration != null) verifyCertificateTrust(showProgress = false)
            }
        }
        scope.launch {
            useCases.observeConnection.state.collect { connection ->
                mutableState.update { it.copy(connection = connection) }
            }
        }
        scope.launch {
            useCases.observeInspection.state.collect { inspection ->
                mutableState.update { it.copy(inspection = inspection) }
            }
        }
        scope.launch {
            useCases.observeNetwork.state.collect { network ->
                mutableState.update { it.copy(network = network) }
            }
        }
    }

    /** Dispatches one intent. It never blocks the caller or changes product focus/navigation. */
    public fun dispatch(action: CompanionAction) {
        if (closed) return
        when (action) {
            is CompanionAction.InvitationSubmitted -> acceptInvitation(action.payload)
            is CompanionAction.PairSubmitted -> pair(action.deviceDisplayName)
            is CompanionAction.RegistrationSelected -> launchOperation {
                if (!useCases.selectRegistration.execute(action.desktopId)) {
                    showFailure(
                        CompanionFailure(
                            CompanionFailureCode.REGISTRATION_NOT_FOUND,
                            "The selected desktop is no longer registered.",
                            true,
                        ),
                    )
                }
            }
            CompanionAction.ConnectRequested -> connect()
            CompanionAction.DisconnectRequested -> launchOperation { useCases.disconnect.execute() }
            CompanionAction.StartInspectionRequested -> startInspection()
            is CompanionAction.VpnConsentResolved -> if (action.granted) startInspection() else showFailure(
                CompanionFailure(CompanionFailureCode.VPN_PERMISSION_DENIED, "VPN permission was not granted.", true),
            )
            CompanionAction.StopInspectionRequested -> launchOperation { useCases.stopInspection.execute() }
            CompanionAction.DownloadCertificateRequested -> downloadCertificate()
            CompanionAction.VerifyCertificateTrustRequested -> verifyCertificateTrust(showProgress = true)
            CompanionAction.OpenCertificateTrustSettingsRequested -> emitEffect(CompanionEffect.OpenCertificateTrustSettings)
            CompanionAction.RefreshCredentialRequested -> refreshCredential()
            is CompanionAction.ForgetDesktopRequested -> launchOperation { useCases.forgetDesktop.execute(action.desktopId) }
            CompanionAction.ClearFailure -> mutableState.update { it.copy(failure = null) }
        }
    }

    /** Prevents new actions/effects; the owner remains responsible for cancelling its lifecycle scope. */
    public fun close() {
        if (closed) return
        closed = true
        pendingInvitation = null
        effectChannel.close()
        scope.cancel()
    }

    private fun acceptInvitation(payload: String) {
        when (val result = useCases.acceptInvitation.execute(payload)) {
            is AcceptPairingInvitationResult.Accepted -> {
                pendingInvitation = result.invitation
                mutableState.update {
                    it.copy(invitationDesktopName = result.invitation.desktopDisplayName, failure = null)
                }
            }
            is AcceptPairingInvitationResult.Rejected -> {
                pendingInvitation = null
                mutableState.update { it.copy(invitationDesktopName = null) }
                showFailure(result.failure)
            }
        }
    }

    private fun pair(deviceDisplayName: String) {
        if (mutableState.value.pairingInProgress) return
        val invitation = pendingInvitation ?: return showFailure(
            CompanionFailure(CompanionFailureCode.INVITATION_INVALID, "Scan or paste a valid pairing invitation first.", true),
        )
        mutableState.update { it.copy(pairingInProgress = true, failure = null) }
        scope.launch {
            try {
                when (val result = useCases.pair.execute(invitation, deviceDisplayName)) {
                    is PairCompanionDeviceResult.Paired -> {
                        pendingInvitation = null
                        mutableState.update { it.copy(invitationDesktopName = null) }
                    }
                    is PairCompanionDeviceResult.Rejected -> showFailure(result.failure)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showFailure(unknownFailure())
            } finally {
                mutableState.update { it.copy(pairingInProgress = false) }
            }
        }
    }

    private fun connect(): Unit = launchOperation {
        when (val result = useCases.connect.execute()) {
            ConnectCompanionResult.Connected -> Unit
            is ConnectCompanionResult.Rejected -> showFailure(result.failure)
        }
    }

    private fun startInspection(): Unit = launchOperation {
        when (val result = useCases.startInspection.execute()) {
            is StartCompanionInspectionResult.Started -> Unit
            StartCompanionInspectionResult.VpnConsentRequired -> emitEffect(CompanionEffect.RequestVpnConsent)
            is StartCompanionInspectionResult.Rejected -> showFailure(result.failure)
        }
    }

    private fun downloadCertificate(): Unit = launchOperation {
        when (val result = useCases.downloadCertificate.execute()) {
            is DownloadCompanionRootCertificateResult.Downloaded ->
                emitEffect(CompanionEffect.InstallCertificate(result.artifact))
            is DownloadCompanionRootCertificateResult.Rejected -> showFailure(result.failure)
        }
    }

    private fun verifyCertificateTrust(showProgress: Boolean) {
        val operation: suspend () -> Unit = {
            val certificate = useCases.verifyCertificateTrust.execute()
            mutableState.update { it.copy(certificate = certificate) }
        }
        if (showProgress) {
            launchOperation(operation)
        } else {
            scope.launch {
                try {
                    operation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    showFailure(unknownFailure())
                }
            }
        }
    }

    private fun refreshCredential(): Unit = launchOperation {
        when (val result = useCases.refreshCredential.execute()) {
            is RefreshCompanionCredentialResult.Refreshed -> Unit
            is RefreshCompanionCredentialResult.Rejected -> showFailure(result.failure)
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        activeOperationCount.update { it + 1 }
        mutableState.update { it.copy(operationInProgress = true, failure = null) }
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showFailure(unknownFailure())
            } finally {
                var remainingOperations = 0
                activeOperationCount.update { current ->
                    (current - 1).coerceAtLeast(0).also { remainingOperations = it }
                }
                mutableState.update { it.copy(operationInProgress = remainingOperations > 0) }
            }
        }
    }

    private fun emitEffect(effect: CompanionEffect) {
        if (!effectChannel.trySend(effect).isSuccess) {
            showFailure(
                CompanionFailure(CompanionFailureCode.UNKNOWN, "Unable to deliver the requested platform action.", true),
            )
        }
    }

    private fun showFailure(failure: CompanionFailure) {
        mutableState.update { it.copy(failure = failure) }
    }

    private fun unknownFailure(): CompanionFailure = CompanionFailure(
        code = CompanionFailureCode.UNKNOWN,
        message = "The companion operation could not be completed.",
        recoverable = true,
    )
}
