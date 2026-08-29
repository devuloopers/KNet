package com.devuloopers.knet.companion.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationResult
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateResult
import com.devuloopers.knet.companion.application.usecase.CompleteCompanionCertificateEnrollmentResult
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceResult
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialResult
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionResult
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionDesktopAvailability
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
            certificateEnrollment = dependencies.observeRegistrations.activeRegistration.value?.let { registration ->
                dependencies.observeCertificateEnrollments.enrollments.value.firstOrNull { it.matches(registration) }
            },
            connection = dependencies.observeConnection.state.value,
            inspection = dependencies.observeInspection.state.value,
            network = dependencies.observeNetwork.state.value,
            discovery = dependencies.observeDiscovery.state.value,
        ),
    )
    private val activeOperationCount: MutableStateFlow<Int> = MutableStateFlow(0)
    private val effectChannel: Channel<CompanionEffect> = Channel(capacity = Channel.BUFFERED)
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
                        certificateEnrollment = registration?.let { active ->
                            dependencies.observeCertificateEnrollments.enrollments.value.firstOrNull {
                                it.matches(active)
                            }
                        },
                    )
                }
                if (registration != null) verifyCertificateTrust(showProgress = false)
            }
        }
        viewModelScope.launch {
            dependencies.observeCertificateEnrollments.enrollments.collect { enrollments ->
                mutableState.update { current ->
                    current.copy(
                        certificateEnrollment = current.activeRegistration?.let { registration ->
                            enrollments.firstOrNull { it.matches(registration) }
                        },
                    )
                }
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
            dependencies.monitorDesktopAvailability.state.collect { availability ->
                mutableState.update { current -> current.copy(desktopAvailability = availability) }
                if (
                    availability is CompanionDesktopAvailability.Available &&
                    mutableState.value.certificate is CompanionCertificateState.VerificationDeferred &&
                    certificateVerificationJob?.isActive != true
                ) {
                    verifyCertificateTrust(showProgress = false)
                }
            }
        }
        viewModelScope.launch {
            mutableState
                .map(::shouldMonitorDesktopAvailability)
                .distinctUntilChanged()
                .collectLatest { shouldMonitor ->
                    if (shouldMonitor) dependencies.monitorDesktopAvailability.execute()
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
            CompanionAction.PickInvitationImageRequested -> {
                mutableState.update { current -> current.copy(invitationScannerVisible = true, failure = null) }
                emitEffect(CompanionEffect.PickInvitationImage)
            }
            is CompanionAction.InvitationScanned -> {
                if (mutableState.value.invitationScannerVisible && !mutableState.value.operationInProgress) {
                    acceptInvitation(action.payload)
                }
            }
            is CompanionAction.InvitationImageDecodeFailed -> {
                showFailure(
                    CompanionFailure(
                        code = CompanionFailureCode.INVITATION_INVALID,
                        message = action.message ?: "No valid KNet QR code found in selected image.",
                        recoverable = true,
                    ),
                )
            }
            CompanionAction.InvitationScannerDismissed -> {
                dismissInvitation()
                mutableState.update { current -> current.copy(failure = null) }
            }
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
            CompanionAction.ContinueCertificateSetupRequested -> continueCertificateSetup()
            CompanionAction.OpenCertificateTrustSettingsRequested -> {
                mutableState.update { current -> current.copy(failure = null) }
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
        invitationResolutionJob = null
        certificateVerificationJob = null
        certificateDownloadJob = null
        endpointMaintenanceJob = null
        effectChannel.close()
    }

    private fun acceptInvitation(payload: String) {
        invitationResolutionJob?.cancel()
        invitationResolutionVersion += 1L
        val expectedVersion = invitationResolutionVersion
        beginOperation()
        invitationResolutionJob = viewModelScope.launch {
            try {
                when (val result = dependencies.acceptInvitation.execute(payload)) {
                    is AcceptPairingInvitationResult.Accepted -> if (expectedVersion == invitationResolutionVersion) {
                        completeAutomaticPairing(result.invitation, expectedVersion)
                    }

                    is AcceptPairingInvitationResult.Rejected -> if (expectedVersion == invitationResolutionVersion) {
                        dismissInvitation(cancelResolution = false)
                        restoreScannerAfterRejectedCameraInput()
                        showFailure(result.failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (expectedVersion == invitationResolutionVersion) {
                    restoreScannerAfterRejectedCameraInput()
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
        mutableState.update { current ->
            current.copy(invitationScannerVisible = false)
        }
    }

    private fun restoreScannerAfterRejectedCameraInput() {
        mutableState.update { current -> current.copy(invitationScannerVisible = true) }
    }

    private suspend fun completeAutomaticPairing(
        invitation: CompanionPairingInvitation,
        expectedVersion: Long,
    ) {
        when (val result = dependencies.pair.execute(invitation)) {
            is PairCompanionDeviceResult.Paired -> if (expectedVersion == invitationResolutionVersion) {
                mutableState.update { current ->
                    current.copy(
                        activeRegistration = result.registration,
                        invitationScannerVisible = false,
                        failure = null,
                    )
                }
            }
            is PairCompanionDeviceResult.Rejected -> if (expectedVersion == invitationResolutionVersion) {
                restoreScannerAfterRejectedCameraInput()
                showFailure(result.failure)
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
        verifyCertificateTrust(showProgress = false)
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
        val previouslyTrusted = mutableState.value.certificate as? CompanionCertificateState.Trusted
        KNetLogger.info(LogTags.CERTIFICATE) { "companion_event=verification_started" }
        mutableState.update { current ->
            if (!showProgress && current.certificate is CompanionCertificateState.Trusted) {
                current
            } else {
                current.copy(certificate = CompanionCertificateState.Verifying)
            }
        }
        if (showProgress) beginOperation()
        val verificationJob = viewModelScope.launch {
            try {
                val certificate = dependencies.verifyCertificateTrust.execute(expectedDesktopId)
                logCertificateVerificationResult(certificate)
                mutableState.update { current ->
                    if (current.activeRegistration?.desktopId == expectedDesktopId) {
                        current.copy(
                            certificate = if (
                                certificate is CompanionCertificateState.VerificationDeferred &&
                                previouslyTrusted != null &&
                                previouslyTrusted.rootCertificateSha256 == current.activeRegistration?.rootCertificateSha256
                            ) {
                                previouslyTrusted
                            } else {
                                certificate
                            },
                        )
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

    private fun continueCertificateSetup() {
        val current = mutableState.value
        val desktopId = current.activeRegistration?.desktopId ?: return showFailure(registrationMissingFailure())
        if (current.certificate !is CompanionCertificateState.Trusted ||
            current.certificateExport !is CompanionCertificateExportState.Saved
        ) {
            return
        }
        launchOperation {
            when (val result = dependencies.completeCertificateEnrollment.execute(desktopId)) {
                is CompleteCompanionCertificateEnrollmentResult.Completed -> {
                    mutableState.update { latest ->
                        if (latest.activeRegistration?.desktopId == desktopId) {
                            latest.copy(
                                certificate = result.trust,
                                certificateEnrollment = result.enrollment,
                                failure = null,
                            )
                        } else {
                            latest
                        }
                    }
                }

                is CompleteCompanionCertificateEnrollmentResult.Rejected -> showFailure(result.failure)
            }
        }
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
            is CompanionCertificateState.VerificationDeferred -> KNetLogger.info(LogTags.CERTIFICATE) {
                "companion_event=verification_deferred reason=${certificate.reason.code}"
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

    private fun shouldMonitorDesktopAvailability(state: CompanionUiState): Boolean =
        state.activeRegistration?.let { state.certificateEnrollment?.matches(it) } == true &&
            (state.inspection == CompanionInspectionState.Stopped || state.inspection is CompanionInspectionState.Failed) &&
            !state.operationInProgress

}
