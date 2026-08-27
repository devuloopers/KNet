package com.devuloopers.knet.companion.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationResult
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateResult
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceResult
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialResult
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionResult
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.presentation.state.CompanionUiState
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.effect.CompanionEffect
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Lifecycle-aware shared companion presentation model.
 *
 * Products create this class through a lifecycle [ViewModel] provider. All asynchronous presentation work is owned
 * by [viewModelScope], while repositories, platform APIs, and process-level resources remain outside this class.
 *
 * @param dependencies Application use cases and observable application state required by the companion flow.
 */
public class CompanionViewModel(
    private val dependencies: CompanionViewModelDependencies,
) : ViewModel() {
    private val mutableState: MutableStateFlow<CompanionUiState> = MutableStateFlow(
        CompanionUiState(
            registrations = dependencies.observeRegistrations.registrations.value,
            activeRegistration = dependencies.observeRegistrations.activeRegistration.value,
            connection = dependencies.observeConnection.state.value,
            inspection = dependencies.observeInspection.state.value,
            network = dependencies.observeNetwork.state.value,
            discovery = dependencies.observeDiscovery.state.value,
        ),
    )
    private val activeOperationCount: MutableStateFlow<Int> = MutableStateFlow(0)
    private val effectChannel: Channel<CompanionEffect> = Channel(capacity = Channel.BUFFERED)
    private var pendingInvitation: CompanionPairingInvitation? = null
    private var invitationResolutionJob: Job? = null
    private var invitationResolutionVersion: Long = 0L
    private var certificateVerificationJob: Job? = null
    private var certificateDownloadJob: Job? = null
    private var endpointMaintenanceJob: Job? = null

    /** Current immutable screen state. */
    public val state: StateFlow<CompanionUiState> = mutableState.asStateFlow()

    /** Single-consumer native effect stream owned by the product host. */
    public val effects: Flow<CompanionEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            dependencies.observeRegistrations.registrations.collect { registrations ->
                mutableState.update { current -> current.copy(registrations = registrations) }
            }
        }
        viewModelScope.launch {
            dependencies.observeRegistrations.activeRegistration.collect { registration ->
                certificateVerificationJob?.cancel()
                certificateDownloadJob?.cancel()
                mutableState.update { current ->
                    current.copy(
                        activeRegistration = registration,
                        certificate = CompanionCertificateState.Unknown,
                        certificateExport = CompanionCertificateExportState.Idle,
                    )
                }
                if (registration != null) verifyCertificateTrust(showProgress = false)
            }
        }
        viewModelScope.launch {
            dependencies.observeConnection.state.collect { connection ->
                mutableState.update { current -> current.copy(connection = connection) }
            }
        }
        viewModelScope.launch {
            dependencies.observeInspection.state.collect { inspection ->
                mutableState.update { current -> current.copy(inspection = inspection) }
                if (inspection is CompanionInspectionState.Running) {
                    if (endpointMaintenanceJob?.isActive != true) {
                        endpointMaintenanceJob = viewModelScope.launch {
                            dependencies.maintainEndpoint.execute()
                        }
                    }
                } else {
                    endpointMaintenanceJob?.cancel()
                    endpointMaintenanceJob = null
                }
            }
        }
        viewModelScope.launch {
            dependencies.observeNetwork.state.collect { network ->
                mutableState.update { current -> current.copy(network = network) }
            }
        }
        viewModelScope.launch {
            dependencies.observeDiscovery.state.collect { discovery ->
                mutableState.update { current -> current.copy(discovery = discovery) }
            }
        }
        viewModelScope.launch {
            dependencies.observeCertificateStoreChanges.changes.collect {
                if (mutableState.value.activeRegistration != null) verifyCertificateTrust(showProgress = false)
            }
        }
    }

    /** Dispatches one intent without blocking the caller or directly changing product navigation. */
    public fun dispatch(action: CompanionAction) {
        when (action) {
            CompanionAction.ScanInvitationRequested -> {
                mutableState.update { current -> current.copy(invitationScannerVisible = true, failure = null) }
            }
            CompanionAction.ImportInvitationImageRequested -> {
                emitEffect(CompanionEffect.RequestInvitationImageImport)
            }
            is CompanionAction.InvitationScanned -> {
                if (mutableState.value.invitationScannerVisible && !mutableState.value.operationInProgress) {
                    acceptInvitation(action.payload, InvitationSource.CAMERA)
                }
            }
            CompanionAction.InvitationScannerDismissed -> {
                dismissInvitation()
                mutableState.update { current -> current.copy(failure = null) }
            }
            is CompanionAction.InvitationSubmitted -> acceptInvitation(action.payload, InvitationSource.ENTRY)
            CompanionAction.InvitationDismissed -> dismissInvitation()
            is CompanionAction.PairSubmitted -> pair(action.deviceDisplayName)
            is CompanionAction.RegistrationSelected -> {
                mutableState.update { current -> current.copy(invitationScannerVisible = false) }
                launchOperation {
                    if (!dependencies.selectRegistration.execute(action.desktopId)) {
                        showFailure(
                            CompanionFailure(
                                CompanionFailureCode.REGISTRATION_NOT_FOUND,
                                "The selected desktop is no longer registered.",
                                true,
                            )
                        )
                    }
                }
            }

            CompanionAction.StartInspectionRequested -> startInspection()
            CompanionAction.VpnConsentRequested -> emitEffect(CompanionEffect.RequestVpnConsent)
            is CompanionAction.VpnConsentResolved -> {
                if (action.granted) {
                    mutableState.update { current -> current.copy(inspectionPermissionRequired = false) }
                    startInspection()
                } else {
                    mutableState.update { current -> current.copy(inspectionPermissionRequired = false) }
                    showFailure(
                        CompanionFailure(
                            CompanionFailureCode.VPN_PERMISSION_DENIED,
                            "VPN permission was not granted.",
                            true
                        )
                    )
                }
            }

            CompanionAction.InspectionPermissionDismissed -> {
                mutableState.update { current -> current.copy(inspectionPermissionRequired = false) }
            }

            CompanionAction.StopInspectionRequested -> launchOperation { dependencies.stopInspection.execute() }
            CompanionAction.DownloadCertificateRequested -> downloadCertificate()
            is CompanionAction.CertificateExportCompleted -> completeCertificateExport(action)
            is CompanionAction.CertificateExportFailed -> failCertificateExport(action.desktopId)
            is CompanionAction.CertificateExportCancelled -> cancelCertificateExport(action.desktopId)
            CompanionAction.VerifyCertificateTrustRequested -> verifyCertificateTrust(showProgress = true)
            CompanionAction.OpenCertificateTrustSettingsRequested -> {
                emitEffect(CompanionEffect.OpenCertificateTrustSettings)
            }

            CompanionAction.RefreshCredentialRequested -> refreshCredential()
            is CompanionAction.ForgetDesktopRequested -> launchOperation {
                dependencies.forgetDesktop.execute(action.desktopId)
            }

            CompanionAction.ClearFailure -> mutableState.update { current -> current.copy(failure = null) }
        }
    }

    /** Clears presentation-only secrets and effect delivery after lifecycle-owned work has been cancelled. */
    override fun onCleared() {
        endpointMaintenanceJob?.cancel()
        pendingInvitation = null
        invitationResolutionJob = null
        certificateVerificationJob = null
        certificateDownloadJob = null
        endpointMaintenanceJob = null
        effectChannel.close()
    }

    private fun acceptInvitation(payload: String, source: InvitationSource) {
        invitationResolutionJob?.cancel()
        invitationResolutionVersion += 1L
        val expectedVersion = invitationResolutionVersion
        beginOperation()
        invitationResolutionJob = viewModelScope.launch {
            try {
                when (val result = dependencies.acceptInvitation.execute(payload)) {
                    is AcceptPairingInvitationResult.Accepted -> if (expectedVersion == invitationResolutionVersion) {
                        pendingInvitation = result.invitation
                        mutableState.update { current ->
                            current.copy(
                                invitationDesktopName = result.invitation.desktopDisplayName,
                                invitationScannerVisible = false,
                                failure = null,
                            )
                        }
                    }

                    is AcceptPairingInvitationResult.Rejected -> if (expectedVersion == invitationResolutionVersion) {
                        dismissInvitation(cancelResolution = false)
                        restoreScannerAfterRejectedCameraInput(source)
                        showFailure(result.failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (expectedVersion == invitationResolutionVersion) {
                    restoreScannerAfterRejectedCameraInput(source)
                    showFailure(unknownFailure())
                }
            } finally {
                endOperation()
            }
        }
    }

    private fun dismissInvitation(cancelResolution: Boolean = true) {
        if (cancelResolution) {
            invitationResolutionVersion += 1L
            invitationResolutionJob?.cancel()
            invitationResolutionJob = null
        }
        pendingInvitation = null
        mutableState.update { current ->
            current.copy(invitationDesktopName = null, invitationScannerVisible = false)
        }
    }

    private fun restoreScannerAfterRejectedCameraInput(source: InvitationSource) {
        if (source == InvitationSource.CAMERA) {
            mutableState.update { current -> current.copy(invitationScannerVisible = true) }
        }
    }

    private fun pair(deviceDisplayName: String) {
        if (mutableState.value.pairingInProgress) return
        val invitation = pendingInvitation ?: return showFailure(
            CompanionFailure(
                CompanionFailureCode.INVITATION_INVALID,
                "Scan or paste a valid pairing invitation first.",
                true,
            ),
        )
        mutableState.update { current -> current.copy(pairingInProgress = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = dependencies.pair.execute(invitation, deviceDisplayName)) {
                    is PairCompanionDeviceResult.Paired -> dismissInvitation()
                    is PairCompanionDeviceResult.Rejected -> showFailure(result.failure)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showFailure(unknownFailure())
            } finally {
                mutableState.update { current -> current.copy(pairingInProgress = false) }
            }
        }
    }

    private fun startInspection(): Unit = launchOperation {
        when (val result = dependencies.startInspection.execute()) {
            is StartCompanionInspectionResult.Started -> Unit
            StartCompanionInspectionResult.VpnConsentRequired -> {
                mutableState.update { current -> current.copy(inspectionPermissionRequired = true) }
            }

            is StartCompanionInspectionResult.Rejected -> showFailure(result.failure)
        }
    }

    private fun downloadCertificate() {
        val desktopId = mutableState.value.activeRegistration?.desktopId ?: return showFailure(registrationMissingFailure())
        if (mutableState.value.certificateExport is CompanionCertificateExportState.Saving) return
        certificateDownloadJob?.cancel()
        mutableState.update { current ->
            current.copy(certificateExport = CompanionCertificateExportState.Saving(desktopId), failure = null)
        }
        certificateDownloadJob = viewModelScope.launch {
            try {
                when (val result = dependencies.downloadCertificate.execute()) {
                    is DownloadCompanionRootCertificateResult.Downloaded -> {
                        if (mutableState.value.activeRegistration?.desktopId != desktopId) return@launch
                        if (!emitEffect(CompanionEffect.ExportCertificate(desktopId, result.artifact))) {
                            failCertificateExport(desktopId)
                        }
                    }

                    is DownloadCompanionRootCertificateResult.Rejected -> {
                        KNetLogger.warn(LogTags.CERTIFICATE) {
                            "companion_event=download_rejected reason=${result.failure.code}"
                        }
                        updateCertificateExportIfCurrent(desktopId, CompanionCertificateExportState.Failed(desktopId))
                        showFailure(result.failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateCertificateExportIfCurrent(desktopId, CompanionCertificateExportState.Failed(desktopId))
                showFailure(unknownFailure())
            }
        }
    }

    private fun completeCertificateExport(action: CompanionAction.CertificateExportCompleted) {
        val saving = mutableState.value.certificateExport as? CompanionCertificateExportState.Saving ?: return
        if (saving.desktopId != action.desktopId || mutableState.value.activeRegistration?.desktopId != action.desktopId) return
        mutableState.update { current ->
            current.copy(
                certificateExport = CompanionCertificateExportState.Saved(
                    desktopId = action.desktopId,
                    fileName = action.fileName,
                    locationDescription = action.locationDescription,
                ),
                failure = null,
            )
        }
    }

    private fun failCertificateExport(desktopId: CompanionDesktopId) {
        val saving = mutableState.value.certificateExport as? CompanionCertificateExportState.Saving ?: return
        if (saving.desktopId != desktopId || mutableState.value.activeRegistration?.desktopId != desktopId) return
        KNetLogger.warn(LogTags.CERTIFICATE) { "companion_event=export_failed" }
        mutableState.update { current ->
            current.copy(certificateExport = CompanionCertificateExportState.Failed(desktopId))
        }
        showFailure(
            CompanionFailure(
                CompanionFailureCode.PERSISTENCE_FAILED,
                "Unable to save the KNet certificate to this device.",
                true,
            ),
        )
    }

    private fun cancelCertificateExport(desktopId: CompanionDesktopId) {
        val saving = mutableState.value.certificateExport as? CompanionCertificateExportState.Saving ?: return
        if (saving.desktopId != desktopId || mutableState.value.activeRegistration?.desktopId != desktopId) return
        mutableState.update { current -> current.copy(certificateExport = CompanionCertificateExportState.Idle) }
    }

    private fun updateCertificateExportIfCurrent(
        desktopId: CompanionDesktopId,
        exportState: CompanionCertificateExportState,
    ) {
        mutableState.update { current ->
            if (current.activeRegistration?.desktopId == desktopId) {
                current.copy(certificateExport = exportState)
            } else {
                current
            }
        }
    }

    private fun verifyCertificateTrust(showProgress: Boolean) {
        certificateVerificationJob?.cancel()
        val expectedDesktopId = mutableState.value.activeRegistration?.desktopId
        KNetLogger.info(LogTags.CERTIFICATE) { "companion_event=verification_started" }
        mutableState.update { current -> current.copy(certificate = CompanionCertificateState.Verifying) }
        if (showProgress) beginOperation()
        val verificationJob = viewModelScope.launch {
            try {
                val certificate = dependencies.verifyCertificateTrust.execute(expectedDesktopId)
                logCertificateVerificationResult(certificate)
                mutableState.update { current ->
                    if (current.activeRegistration?.desktopId == expectedDesktopId) {
                        current.copy(certificate = certificate)
                    } else {
                        current
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                KNetLogger.error(LogTags.CERTIFICATE) {
                    "companion_event=verification_failed reason=${failure::class.simpleName ?: "unknown"}"
                }
                if (mutableState.value.activeRegistration?.desktopId == expectedDesktopId) {
                    showFailure(unknownFailure())
                }
            }
        }
        if (showProgress) verificationJob.invokeOnCompletion { endOperation() }
        certificateVerificationJob = verificationJob
    }

    private fun logCertificateVerificationResult(certificate: CompanionCertificateState) {
        when (certificate) {
            CompanionCertificateState.Unknown -> KNetLogger.warn(LogTags.CERTIFICATE) {
                "companion_event=verification_completed result=unknown"
            }
            CompanionCertificateState.InstallationRequired -> KNetLogger.info(LogTags.CERTIFICATE) {
                "companion_event=verification_completed result=installation_required"
            }
            CompanionCertificateState.Verifying -> KNetLogger.debug(LogTags.CERTIFICATE) {
                "companion_event=verification_completed result=still_verifying"
            }
            is CompanionCertificateState.Trusted -> KNetLogger.info(LogTags.CERTIFICATE) {
                "companion_event=verification_completed result=trusted"
            }
            is CompanionCertificateState.Rejected -> KNetLogger.warn(LogTags.CERTIFICATE) {
                "companion_event=verification_completed result=rejected reason=${certificate.reason.code}"
            }
        }
    }

    private fun refreshCredential(): Unit = launchOperation {
        when (val result = dependencies.refreshCredential.execute()) {
            is RefreshCompanionCredentialResult.Refreshed -> Unit
            is RefreshCompanionCredentialResult.Rejected -> showFailure(result.failure)
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        beginOperation()
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showFailure(unknownFailure())
            } finally {
                endOperation()
            }
        }
    }

    private fun beginOperation() {
        activeOperationCount.update { operationCount -> operationCount + 1 }
        mutableState.update { current -> current.copy(operationInProgress = true, failure = null) }
    }

    private fun endOperation() {
        var remainingOperations = 0
        activeOperationCount.update { operationCount ->
            (operationCount - 1).coerceAtLeast(0).also { remaining -> remainingOperations = remaining }
        }
        mutableState.update { current -> current.copy(operationInProgress = remainingOperations > 0) }
    }

    private fun emitEffect(effect: CompanionEffect): Boolean {
        val delivered = effectChannel.trySend(effect).isSuccess
        if (!delivered) {
            showFailure(
                CompanionFailure(
                    CompanionFailureCode.UNKNOWN,
                    "Unable to deliver the requested platform action.",
                    true,
                ),
            )
        }
        return delivered
    }

    private fun showFailure(failure: CompanionFailure) {
        mutableState.update { current -> current.copy(failure = failure) }
    }

    private fun unknownFailure(): CompanionFailure = CompanionFailure(
        code = CompanionFailureCode.UNKNOWN,
        message = "The companion operation could not be completed.",
        recoverable = true,
    )

    private fun registrationMissingFailure(): CompanionFailure = CompanionFailure(
        code = CompanionFailureCode.REGISTRATION_NOT_FOUND,
        message = "No paired desktop is selected.",
        recoverable = true,
    )

    private enum class InvitationSource {
        ENTRY,
        CAMERA,
    }
}
