package com.devuloopers.knet.companion.presentation

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devuloopers.knet.companion.application.contract.CompanionCertificateArtifact
import com.devuloopers.knet.companion.application.contract.CompanionCertificateEnrollmentRepository
import com.devuloopers.knet.companion.application.contract.CompanionCertificateDownloadResult
import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionCredentialRefreshResult
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointRecoveryResult
import com.devuloopers.knet.companion.application.contract.CompanionEndpointResolver
import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInspectionPreparationResult
import com.devuloopers.knet.companion.application.contract.CompanionInspectionStartResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolutionResult
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionPairingClientResult
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.contract.CompanionTransportResult
import com.devuloopers.knet.companion.application.contract.InvitationDecodeResult
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationUseCase
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.CompleteCompanionCertificateEnrollmentUseCase
import com.devuloopers.knet.companion.application.usecase.CompanionDesktopAvailabilityMonitor
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateUseCase
import com.devuloopers.knet.companion.application.usecase.MaintainCompanionEndpointUseCase
import com.devuloopers.knet.companion.application.usecase.ForgetCompanionDesktopUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateStoreChangesUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateEnrollmentsUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionConnectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionNetworkUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionDiscoveryUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionRegistrationsUseCase
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceUseCase
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialUseCase
import com.devuloopers.knet.companion.application.usecase.RecoverCompanionEndpointUseCase
import com.devuloopers.knet.companion.application.usecase.SelectCompanionRegistrationUseCase
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.StopCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.VerifyCompanionCertificateTrustUseCase
import com.devuloopers.knet.companion.model.CompanionCertificateState
import com.devuloopers.knet.companion.model.CompanionCertificateEnrollment
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapSecret
import com.devuloopers.knet.companion.model.CompanionConnectionState
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopAvailability
import com.devuloopers.knet.companion.model.CompanionDeviceIdentity
import com.devuloopers.knet.companion.model.CompanionDeviceDisplayName
import com.devuloopers.knet.companion.model.CompanionDiscoveryState
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionPairingBootstrap
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.CompanionTransportKind
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.presentation.action.CompanionAction
import com.devuloopers.knet.companion.presentation.effect.CompanionEffect
import com.devuloopers.knet.companion.presentation.flow.CompanionFlowStage
import com.devuloopers.knet.companion.presentation.flow.resolveFlowStage
import com.devuloopers.knet.companion.presentation.state.CompanionCertificateExportState
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModel
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModelDependencies
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.DeviceScope
import com.devuloopers.knet.pairing.PairingInvitation
import com.devuloopers.knet.pairing.PairingInvitationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation

@OptIn(ExperimentalCoroutinesApi::class)
private fun runCompanionViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {
    @Test
    fun currentRepositoryStateIsAvailableBeforeCollectorsAreScheduled() = runCompanionViewModelTest {
        val activeRegistration = registration()
        val fixture = Fixture(activeRegistration = activeRegistration)

        val viewModel = fixture.viewModel()

        assertEquals(activeRegistration, viewModel.state.value.activeRegistration)
        assertEquals(listOf(activeRegistration), viewModel.state.value.registrations)
        assertIs<CompanionNetworkState.Available>(viewModel.state.value.network)
        fixture.clearViewModelStore()
    }

    @Test
    fun completedCertificateEnrollmentRestoresHomeBeforeBackgroundVerificationRuns() =
        runCompanionViewModelTest {
            val activeRegistration = registration()
            val fixture = Fixture(activeRegistration = activeRegistration)
            fixture.repository.mutableEnrollments.value = listOf(
                CompanionCertificateEnrollment(
                    activeRegistration.desktopId,
                    activeRegistration.rootCertificateSha256,
                    completedAtEpochMillis = 900L,
                ),
            )
            fixture.credentials.values[activeRegistration.credentialReference] = "credential"

            val viewModel = fixture.viewModel()

            assertEquals(CompanionFlowStage.INSPECTION_HOME, viewModel.state.value.resolveFlowStage())
            assertIs<CompanionCertificateState.Unknown>(viewModel.state.value.certificate)
            advanceUntilIdle()
            assertIs<CompanionCertificateState.Trusted>(viewModel.state.value.certificate)
            assertEquals(CompanionFlowStage.INSPECTION_HOME, viewModel.state.value.resolveFlowStage())
            fixture.clearViewModelStore()
        }

    @Test
    fun invitationSecretNeverEntersObservableUiState() = runCompanionViewModelTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()

        viewModel.dispatch(CompanionAction.ScanInvitationRequested)
        viewModel.dispatch(CompanionAction.InvitationScanned("invitation"))
        advanceUntilIdle()

        assertEquals(registration().desktopId, viewModel.state.value.activeRegistration?.desktopId)
        assertFalse(viewModel.state.value.toString().contains(INVITATION_SECRET))
        fixture.clearViewModelStore()
    }

    @Test
    fun vpnConsentExplanationPrecedesTypedNativeEffectAndCapture() = runCompanionViewModelTest {
        val fixture = Fixture(activeRegistration = registration())
        fixture.credentials.values[registration().credentialReference] = "credential"
        fixture.inspection.preparation = CompanionInspectionPreparationResult.ConsentRequired
        val viewModel = fixture.viewModel()

        viewModel.dispatch(CompanionAction.StartInspectionRequested)
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.inspectionPermissionRequired)
        assertEquals(0, fixture.inspection.startCalls)

        viewModel.dispatch(CompanionAction.VpnConsentRequested)

        assertIs<CompanionEffect.RequestVpnConsent>(viewModel.effects.first())
        assertEquals(0, fixture.inspection.startCalls)
        assertFalse(viewModel.state.value.operationInProgress)
        fixture.clearViewModelStore()
    }

    @Test
    fun invitationScannerIsInlineStateWithinTheConnectStage() = runCompanionViewModelTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()

        viewModel.dispatch(CompanionAction.ScanInvitationRequested)

        assertEquals(true, viewModel.state.value.invitationScannerVisible)
        assertEquals(CompanionFlowStage.CONNECT_DESKTOP, viewModel.state.value.resolveFlowStage())
        viewModel.dispatch(CompanionAction.InvitationScannerDismissed)
        assertEquals(CompanionFlowStage.CONNECT_DESKTOP, viewModel.state.value.resolveFlowStage())
        fixture.clearViewModelStore()
    }

    @Test
    fun cameraScannerIgnoresDuplicatePayloadWhileTheFirstResolutionIsRunning() = runCompanionViewModelTest {
        val resolutionStarted = CompletableDeferred<Unit>()
        val releaseResolution = CompletableDeferred<Unit>()
        val fixture = Fixture()
        fixture.certificateVerification = { CompanionCertificateState.InstallationRequired }
        fixture.invitationResolution = {
            resolutionStarted.complete(Unit)
            releaseResolution.await()
            CompanionInvitationResolutionResult.Resolved(invitation())
        }
        val viewModel = fixture.viewModel()
        viewModel.dispatch(CompanionAction.ScanInvitationRequested)

        viewModel.dispatch(CompanionAction.InvitationScanned("invitation"))
        resolutionStarted.await()
        viewModel.dispatch(CompanionAction.InvitationScanned("invitation"))

        assertEquals(1, fixture.invitationResolutionCalls)
        releaseResolution.complete(Unit)
        advanceUntilIdle()
        assertEquals(CompanionFlowStage.CERTIFICATE_SETUP, viewModel.state.value.resolveFlowStage())
        fixture.clearViewModelStore()
    }

    @Test
    fun cameraPayloadIsIgnoredWhenTheInlineScannerIsNotActive() = runCompanionViewModelTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()

        viewModel.dispatch(CompanionAction.InvitationScanned("invitation"))
        advanceUntilIdle()

        assertEquals(0, fixture.invitationResolutionCalls)
        assertEquals(CompanionFlowStage.CONNECT_DESKTOP, viewModel.state.value.resolveFlowStage())
        fixture.clearViewModelStore()
    }

    @Test
    fun pickInvitationImageRequestedEmitsNativeEffectAndActivatesScanner() = runCompanionViewModelTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()

        viewModel.dispatch(CompanionAction.PickInvitationImageRequested)

        assertEquals(true, viewModel.state.value.invitationScannerVisible)
        assertIs<CompanionEffect.PickInvitationImage>(viewModel.effects.first())
        fixture.clearViewModelStore()
    }

    @Test
    fun invitationImageDecodeFailedPresentsRecoverableFailure() = runCompanionViewModelTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        viewModel.dispatch(CompanionAction.PickInvitationImageRequested)

        viewModel.dispatch(CompanionAction.InvitationImageDecodeFailed("Custom decode failure"))
        advanceUntilIdle()

        assertEquals(CompanionFlowStage.CONNECT_DESKTOP, viewModel.state.value.resolveFlowStage())
        assertEquals(CompanionFailureCode.INVITATION_INVALID, viewModel.state.value.failure?.code)
        assertEquals("Custom decode failure", viewModel.state.value.failure?.message)
        fixture.clearViewModelStore()
    }

    @Test
    fun rejectedCameraPayloadReturnsToScannerForAnExplicitRetry() = runCompanionViewModelTest {
        val fixture = Fixture()
        fixture.invitationResolution = {
            CompanionInvitationResolutionResult.Rejected(
                CompanionFailure(
                    code = CompanionFailureCode.INVITATION_INVALID,
                    message = "The QR code is not a valid KNet invitation.",
                    recoverable = true,
                ),
            )
        }
        val viewModel = fixture.viewModel()
        viewModel.dispatch(CompanionAction.ScanInvitationRequested)

        viewModel.dispatch(CompanionAction.InvitationScanned("invalid"))
        advanceUntilIdle()

        assertEquals(CompanionFlowStage.CONNECT_DESKTOP, viewModel.state.value.resolveFlowStage())
        assertEquals(true, viewModel.state.value.invitationScannerVisible)
        assertEquals(CompanionFailureCode.INVITATION_INVALID, viewModel.state.value.failure?.code)
        fixture.clearViewModelStore()
    }

    @Test
    fun dismissingScannerCancelsAnInFlightInvitationResolution() = runCompanionViewModelTest {
        val resolutionStarted = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        val fixture = Fixture()
        fixture.invitationResolution = {
            resolutionStarted.complete(Unit)
            neverReleased.await()
            CompanionInvitationResolutionResult.Resolved(invitation())
        }
        val viewModel = fixture.viewModel()
        viewModel.dispatch(CompanionAction.ScanInvitationRequested)
        viewModel.dispatch(CompanionAction.InvitationScanned("invitation"))
        resolutionStarted.await()

        viewModel.dispatch(CompanionAction.InvitationScannerDismissed)
        advanceUntilIdle()

        assertEquals(CompanionFlowStage.CONNECT_DESKTOP, viewModel.state.value.resolveFlowStage())
        assertFalse(viewModel.state.value.operationInProgress)
        fixture.clearViewModelStore()
    }

    @Test
    fun acceptedInvitationPairsAutomaticallyWithoutAConfirmationStage() = runCompanionViewModelTest {
        val fixture = Fixture()
        fixture.certificateVerification = { CompanionCertificateState.InstallationRequired }
        val viewModel = fixture.viewModel()
        viewModel.dispatch(CompanionAction.ScanInvitationRequested)
        viewModel.dispatch(CompanionAction.InvitationScanned("invitation"))
        advanceUntilIdle()

        assertEquals(CompanionFlowStage.CERTIFICATE_SETUP, viewModel.state.value.resolveFlowStage())
        assertEquals(false, viewModel.state.value.invitationScannerVisible)
        assertEquals("Android test device · ICE1", fixture.pairingClient.pairedDisplayName)
        fixture.clearViewModelStore()
    }

    @Test
    fun clearingViewModelStoreStopsRepositoryCollectorsOwnedByTheViewModel() = runCompanionViewModelTest {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        fixture.clearViewModelStore()

        fixture.repository.mutableRegistrations.value = listOf(registration())
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.state.value.registrations)
    }

    @Test
    fun trustStoreNotificationTriggersASecondAuthoritativeVerification() = runCompanionViewModelTest {
        val fixture = Fixture(activeRegistration = registration())
        fixture.credentials.values[registration().credentialReference] = "credential"
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val initialCalls = fixture.certificateVerificationCalls

        fixture.certificateChanges.emit(Unit)
        advanceUntilIdle()

        assertEquals(initialCalls + 1, fixture.certificateVerificationCalls)
        assertIs<CompanionCertificateState.Trusted>(viewModel.state.value.certificate)
        fixture.clearViewModelStore()
    }

    @Test
    fun certificateDownloadWaitsForPlatformExportBeforeShowingInstallationSteps() =
        runCompanionViewModelTest {
            val registration = registration()
            val fixture = Fixture(activeRegistration = registration)
            fixture.credentials.values[registration.credentialReference] = "credential"
            val viewModel = fixture.viewModel()
            advanceUntilIdle()
            val effect = async { viewModel.effects.first() }

            viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
            advanceUntilIdle()

            val export = assertIs<CompanionEffect.ExportCertificate>(effect.await())
            assertEquals(registration.desktopId, export.desktopId)
            assertIs<CompanionCertificateExportState.Saving>(viewModel.state.value.certificateExport)

            viewModel.dispatch(
                CompanionAction.CertificateExportCompleted(
                    desktopId = registration.desktopId,
                    fileName = "KNet-Root-CA.crt",
                    locationDescription = "Downloads/KNet",
                ),
            )

            val saved = assertIs<CompanionCertificateExportState.Saved>(viewModel.state.value.certificateExport)
            assertEquals("KNet-Root-CA.crt", saved.fileName)
            assertEquals("Downloads/KNet", saved.locationDescription)
            fixture.clearViewModelStore()
        }

    @Test
    fun completedCertificateExportTriggersBackgroundTrustVerification() = runCompanionViewModelTest {
        val registration = registration()
        val fixture = Fixture(activeRegistration = registration)
        fixture.credentials.values[registration.credentialReference] = "credential"
        fixture.certificateVerification = { CompanionCertificateState.InstallationRequired }
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val initialVerificationCalls = fixture.certificateVerificationCalls
        val effect = async { viewModel.effects.first() }

        viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
        advanceUntilIdle()
        effect.await()
        viewModel.dispatch(
            CompanionAction.CertificateExportCompleted(
                desktopId = registration.desktopId,
                fileName = "KNet-Root-CA.crt",
                locationDescription = "Downloads/KNet",
            ),
        )
        advanceUntilIdle()

        assertEquals(initialVerificationCalls + 1, fixture.certificateVerificationCalls)
        assertIs<CompanionCertificateState.InstallationRequired>(viewModel.state.value.certificate)
        fixture.clearViewModelStore()
    }

    @Test
    fun continueExplicitlyAcknowledgesDownloadedAndVerifiedCertificateSetup() = runCompanionViewModelTest {
        val registration = registration()
        val fixture = Fixture(activeRegistration = registration)
        fixture.credentials.values[registration.credentialReference] = "credential"
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val exportEffect = async { viewModel.effects.first() }

        viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
        advanceUntilIdle()
        exportEffect.await()
        viewModel.dispatch(
            CompanionAction.CertificateExportCompleted(
                desktopId = registration.desktopId,
                fileName = "KNet-Root-CA.crt",
                locationDescription = "Downloads/KNet",
            ),
        )
        advanceUntilIdle()

        viewModel.dispatch(CompanionAction.ContinueCertificateSetupRequested)
        advanceUntilIdle()

        assertIs<CompanionCertificateState.Trusted>(viewModel.state.value.certificate)
        assertEquals(CompanionFlowStage.INSPECTION_HOME, viewModel.state.value.resolveFlowStage())
        assertFalse(viewModel.state.value.operationInProgress)
        fixture.clearViewModelStore()
    }

    @Test
    fun authenticatedAvailabilityMonitorIsOwnedOnlyByIdleOperationalHome() = runCompanionViewModelTest {
        val registration = registration()
        val fixture = Fixture(activeRegistration = registration)
        fixture.credentials.values[registration.credentialReference] = "credential"
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        assertEquals(0, fixture.desktopAvailabilityMonitor.executeCalls)
        val exportEffect = async { viewModel.effects.first() }

        viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
        advanceUntilIdle()
        exportEffect.await()
        viewModel.dispatch(
            CompanionAction.CertificateExportCompleted(
                desktopId = registration.desktopId,
                fileName = "KNet-Root-CA.crt",
                locationDescription = "Downloads/KNet",
            ),
        )
        advanceUntilIdle()
        viewModel.dispatch(CompanionAction.ContinueCertificateSetupRequested)
        advanceUntilIdle()

        assertEquals(1, fixture.desktopAvailabilityMonitor.executeCalls)
        fixture.desktopAvailabilityMonitor.publish(
            CompanionDesktopAvailability.Available(registration.desktopId, 2_000L),
        )
        advanceUntilIdle()
        assertIs<CompanionDesktopAvailability.Available>(viewModel.state.value.desktopAvailability)

        viewModel.dispatch(CompanionAction.StartInspectionRequested)
        advanceUntilIdle()
        assertEquals(1, fixture.desktopAvailabilityMonitor.cancellationCalls)
        fixture.clearViewModelStore()
    }

    @Test
    fun recoverableBackgroundTrustFailureKeepsPreviouslyVerifiedCertificate() = runCompanionViewModelTest {
        val registration = registration()
        val fixture = Fixture(activeRegistration = registration)
        fixture.credentials.values[registration.credentialReference] = "credential"
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val exportEffect = async { viewModel.effects.first() }
        viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
        advanceUntilIdle()
        exportEffect.await()
        viewModel.dispatch(
            CompanionAction.CertificateExportCompleted(
                desktopId = registration.desktopId,
                fileName = "KNet-Root-CA.crt",
                locationDescription = "Downloads/KNet",
            ),
        )
        advanceUntilIdle()
        viewModel.dispatch(CompanionAction.ContinueCertificateSetupRequested)
        advanceUntilIdle()
        assertEquals(CompanionFlowStage.INSPECTION_HOME, viewModel.state.value.resolveFlowStage())

        val verificationStarted = CompletableDeferred<Unit>()
        val releaseVerification = CompletableDeferred<Unit>()
        fixture.certificateVerification = { activeRegistration ->
            verificationStarted.complete(Unit)
            releaseVerification.await()
            CompanionCertificateState.VerificationDeferred(
                CompanionFailure(
                    CompanionFailureCode.TRANSPORT_UNAVAILABLE,
                    "Desktop unavailable.",
                    true,
                ),
            )
        }
        fixture.certificateChanges.emit(Unit)
        verificationStarted.await()

        assertIs<CompanionCertificateState.Trusted>(viewModel.state.value.certificate)
        assertEquals(CompanionFlowStage.INSPECTION_HOME, viewModel.state.value.resolveFlowStage())
        releaseVerification.complete(Unit)
        advanceUntilIdle()
        assertIs<CompanionCertificateState.Trusted>(viewModel.state.value.certificate)
        fixture.clearViewModelStore()
    }

    @Test
    fun repeatedCertificateDownloadTapDoesNotStartASecondExport() = runCompanionViewModelTest {
        val registration = registration()
        val fixture = Fixture(activeRegistration = registration)
        fixture.credentials.values[registration.credentialReference] = "credential"
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        fixture.certificateDownloadCalls = 0
        val effect = async { viewModel.effects.first() }

        viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
        viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
        advanceUntilIdle()
        effect.await()

        assertEquals(1, fixture.certificateDownloadCalls)
        fixture.clearViewModelStore()
    }

    @Test
    fun cancelledCertificateDocumentSelectionReturnsToIdleWithoutClaimingInstallation() =
        runCompanionViewModelTest {
            val registration = registration()
            val fixture = Fixture(activeRegistration = registration)
            fixture.credentials.values[registration.credentialReference] = "credential"
            val viewModel = fixture.viewModel()
            advanceUntilIdle()
            val effect = async { viewModel.effects.first() }
            viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
            advanceUntilIdle()
            effect.await()

            viewModel.dispatch(CompanionAction.CertificateExportCancelled(registration.desktopId))

            assertIs<CompanionCertificateExportState.Idle>(viewModel.state.value.certificateExport)
            fixture.clearViewModelStore()
        }

    @Test
    fun staleCertificateExportResultCannotUpdateANewlySelectedDesktop() = runCompanionViewModelTest {
        val first = registration()
        val second = registration(
            desktopId = "desktop-2",
            credentialReference = "credential-reference-2",
            rootFingerprint = "c".repeat(64),
        )
        val fixture = Fixture(activeRegistration = first)
        fixture.repository.mutableRegistrations.value = listOf(first, second)
        fixture.credentials.values[first.credentialReference] = "credential-1"
        fixture.credentials.values[second.credentialReference] = "credential-2"
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }
        viewModel.dispatch(CompanionAction.DownloadCertificateRequested)
        advanceUntilIdle()
        effect.await()

        viewModel.dispatch(CompanionAction.RegistrationSelected(second.desktopId))
        advanceUntilIdle()
        viewModel.dispatch(
            CompanionAction.CertificateExportCompleted(
                desktopId = first.desktopId,
                fileName = "KNet-Root-CA.crt",
                locationDescription = "Downloads/KNet",
            ),
        )

        assertEquals(second.desktopId, viewModel.state.value.activeRegistration?.desktopId)
        assertIs<CompanionCertificateExportState.Idle>(viewModel.state.value.certificateExport)
        fixture.clearViewModelStore()
    }

    @Test
    fun completedVerificationCannotOverwriteARegistrationSelectedWhileItWasRunning() = runCompanionViewModelTest {
        val firstRegistration = registration()
        val secondRegistration = registration(
            desktopId = "desktop-2",
            credentialReference = "credential-reference-2",
            rootFingerprint = "c".repeat(64),
        )
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val fixture = Fixture(activeRegistration = firstRegistration)
        fixture.credentials.values[firstRegistration.credentialReference] = "credential-1"
        fixture.credentials.values[secondRegistration.credentialReference] = "credential-2"
        fixture.certificateVerification = { active ->
            if (active.desktopId == firstRegistration.desktopId) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirst.await() }
                CompanionCertificateState.Trusted(active.rootCertificateSha256, 1_000L)
            } else {
                CompanionCertificateState.InstallationRequired
            }
        }
        fixture.repository.mutableRegistrations.value = listOf(firstRegistration, secondRegistration)
        val viewModel = fixture.viewModel()
        firstStarted.await()

        viewModel.dispatch(CompanionAction.RegistrationSelected(secondRegistration.desktopId))
        advanceUntilIdle()
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(secondRegistration.desktopId, viewModel.state.value.activeRegistration?.desktopId)
        assertIs<CompanionCertificateState.InstallationRequired>(viewModel.state.value.certificate)
        fixture.clearViewModelStore()
    }

    private class Fixture(activeRegistration: CompanionRegistration? = null) {
        private val viewModelStore = ViewModelStore()
        val repository = FakeRegistrationRepository(activeRegistration)
        val credentials = FakeCredentialStore()
        val transport = FakeTransport()
        val inspection = FakeInspectionController()
        var certificateDownloadCalls: Int = 0
        private val certificates = CompanionRootCertificateSource { _, _ ->
            certificateDownloadCalls += 1
            CompanionCertificateDownloadResult.Downloaded(
                CompanionCertificateArtifact(byteArrayOf(1), "knet-root-ca.crt"),
            )
        }
        private val installationArtifacts = CompanionCertificateInstallationArtifactSource { registration, credential ->
            certificates.download(registration, credential)
        }
        val certificateChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var certificateVerificationCalls: Int = 0
        var certificateVerification: suspend (CompanionRegistration) -> CompanionCertificateState = { registration ->
            CompanionCertificateState.Trusted(registration.rootCertificateSha256, 1_000L)
        }
        private val certificateVerifier = CompanionCertificateTrustVerifier { registration, _, _ ->
            certificateVerificationCalls += 1
            certificateVerification(registration)
        }
        private val network = CompanionNetworkObserver {
            MutableStateFlow(CompanionNetworkState.Available(metered = false))
        }
        val pairingClient = FakePairingClient()
        var invitationResolutionCalls: Int = 0
        var invitationResolution: suspend (CompanionPairingBootstrap) -> CompanionInvitationResolutionResult = {
            CompanionInvitationResolutionResult.Resolved(invitation())
        }
        private val identityProvider = CompanionDeviceIdentityProvider {
            CompanionDeviceIdentity(
                deviceId = RegisteredDeviceId("device-1"),
                publicKeyEncoded = "public-key",
                privateKeyReference = "private-key-reference",
                proofAlgorithm = DeviceProofAlgorithm.ECDSA_P256_SHA256,
            )
        }
        private val displayNameProvider = CompanionDeviceDisplayNameProvider {
            CompanionDeviceDisplayName("Android test device · ICE1")
        }
        val desktopAvailabilityMonitor = FakeDesktopAvailabilityMonitor()

        fun viewModel(): CompanionViewModel {
            val connect = ConnectCompanionUseCase(
                repository,
                credentials,
                network,
                transport,
                nowEpochMillis = { 1_000L },
                endpointResolver = CompanionEndpointResolver { registration ->
                    CompanionEndpointRecoveryResult.Recovered(
                        registration,
                    )
                },
            )
            val discovery = FakeDesktopDiscovery()
            val recoverEndpoint = RecoverCompanionEndpointUseCase(
                repository,
                credentials,
                discovery,
                CompanionEndpointReconciliationClient { _, _, _ ->
                    CompanionEndpointReconciliationResult.Rejected(
                        CompanionFailure(CompanionFailureCode.TRANSPORT_UNAVAILABLE, "not used", true),
                    )
                },
            )
            val verifyCertificate = VerifyCompanionCertificateTrustUseCase(
                repository,
                credentials,
                certificates,
                certificateVerifier,
                nowEpochMillis = { 1_000L },
            )
            val factory = viewModelFactory {
                initializer {
                    CompanionViewModel(
                        dependencies = CompanionViewModelDependencies(
                            acceptInvitation = AcceptPairingInvitationUseCase(
                                CompanionInvitationCodec { InvitationDecodeResult.Accepted(bootstrap()) },
                                CompanionInvitationResolver { pairingBootstrap ->
                                    invitationResolutionCalls += 1
                                    invitationResolution(pairingBootstrap)
                                },
                            ) { 1_000L },
                            pair = PairCompanionDeviceUseCase(
                                identityProvider,
                                displayNameProvider,
                                pairingClient,
                                credentials,
                                repository,
                            ) { 1_000L },
                            observeRegistrations = ObserveCompanionRegistrationsUseCase(repository),
                            observeCertificateEnrollments = ObserveCompanionCertificateEnrollmentsUseCase(repository),
                            selectRegistration = SelectCompanionRegistrationUseCase(repository),
                            observeConnection = ObserveCompanionConnectionUseCase(transport),
                            observeNetwork = ObserveCompanionNetworkUseCase(network),
                            observeDiscovery = ObserveCompanionDiscoveryUseCase(discovery),
                            maintainEndpoint = MaintainCompanionEndpointUseCase(
                                repository,
                                discovery,
                                recoverEndpoint,
                                transport,
                                connect,
                            ),
                            monitorDesktopAvailability = desktopAvailabilityMonitor,
                            startInspection = StartCompanionInspectionUseCase(
                                repository,
                                connect,
                                verifyCertificate,
                                inspection,
                                transport,
                            ),
                            stopInspection = StopCompanionInspectionUseCase(inspection, transport),
                            observeInspection = ObserveCompanionInspectionUseCase(inspection),
                            downloadCertificate = DownloadCompanionRootCertificateUseCase(
                                repository,
                                credentials,
                                installationArtifacts,
                                nowEpochMillis = { 1_000L },
                            ),
                            verifyCertificateTrust = verifyCertificate,
                            completeCertificateEnrollment = CompleteCompanionCertificateEnrollmentUseCase(
                                repository,
                                repository,
                                verifyCertificate,
                                nowEpochMillis = { 1_000L },
                            ),
                            observeCertificateStoreChanges = ObserveCompanionCertificateStoreChangesUseCase(
                                CompanionCertificateStoreChangeObserver { certificateChanges },
                            ),
                            refreshCredential = RefreshCompanionCredentialUseCase(
                                repository,
                                credentials,
                                pairingClient,
                            ) { 1_000L },
                            forgetDesktop = ForgetCompanionDesktopUseCase(
                                repository,
                                repository,
                                credentials,
                                inspection,
                                transport,
                            ),
                        ),
                    )
                }
            }
            return ViewModelProvider.create(viewModelStore, factory)[CompanionViewModel::class]
        }

        fun clearViewModelStore() {
            viewModelStore.clear()
        }
    }

    private class FakeRegistrationRepository(active: CompanionRegistration?) :
        CompanionRegistrationRepository,
        CompanionCertificateEnrollmentRepository {
        val mutableRegistrations = MutableStateFlow(active?.let(::listOf).orEmpty())
        val mutableEnrollments = MutableStateFlow<List<CompanionCertificateEnrollment>>(emptyList())
        private val mutableActive = MutableStateFlow(active)
        override val registrations: StateFlow<List<CompanionRegistration>> = mutableRegistrations
        override val activeRegistration: StateFlow<CompanionRegistration?> = mutableActive
        override val enrollments: StateFlow<List<CompanionCertificateEnrollment>> = mutableEnrollments

        override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) {
            mutableRegistrations.value = mutableRegistrations.value.filterNot { it.desktopId == registration.desktopId } + registration
            mutableEnrollments.value = mutableEnrollments.value.filterNot {
                it.desktopId == registration.desktopId && !it.matches(registration)
            }
            if (makeActive) mutableActive.value = registration
        }

        override suspend fun setActive(desktopId: CompanionDesktopId?): Boolean {
            val selected = desktopId?.let { id -> mutableRegistrations.value.firstOrNull { it.desktopId == id } }
            if (desktopId != null && selected == null) return false
            mutableActive.value = selected
            return true
        }

        override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? {
            val removed = mutableRegistrations.value.firstOrNull { it.desktopId == desktopId } ?: return null
            mutableRegistrations.value = mutableRegistrations.value.filterNot { it.desktopId == desktopId }
            mutableEnrollments.value = mutableEnrollments.value.filterNot { it.desktopId == desktopId }
            if (mutableActive.value?.desktopId == desktopId) mutableActive.value = null
            return removed
        }

        override suspend fun complete(enrollment: CompanionCertificateEnrollment): Boolean {
            val registration = mutableRegistrations.value.firstOrNull { it.desktopId == enrollment.desktopId }
                ?: return false
            if (!enrollment.matches(registration)) return false
            mutableEnrollments.value = mutableEnrollments.value.filterNot {
                it.desktopId == enrollment.desktopId
            } + enrollment
            return true
        }

        override suspend fun removeEnrollment(desktopId: CompanionDesktopId): Boolean {
            val existed = mutableEnrollments.value.any { it.desktopId == desktopId }
            mutableEnrollments.value = mutableEnrollments.value.filterNot { it.desktopId == desktopId }
            return existed
        }

        override suspend fun migrateIdentity(
            previousDesktopId: CompanionDesktopId,
            registration: CompanionRegistration,
            makeActive: Boolean,
        ): Boolean {
            if (mutableRegistrations.value.none { it.desktopId == previousDesktopId }) return false
            mutableRegistrations.value = mutableRegistrations.value
                .filterNot { it.desktopId == previousDesktopId || it.desktopId == registration.desktopId } + registration
            if (makeActive || mutableActive.value?.desktopId == previousDesktopId) mutableActive.value = registration
            return true
        }
    }

    private class FakeCredentialStore : CompanionCredentialStore {
        val values = mutableMapOf<CompanionCredentialReference, String>()
        override suspend fun write(reference: CompanionCredentialReference, credential: String) {
            values[reference] = credential
        }
        override suspend fun read(reference: CompanionCredentialReference): String? = values[reference]
        override suspend fun remove(reference: CompanionCredentialReference) {
            values.remove(reference)
        }
    }

    private class FakeDesktopDiscovery : CompanionDesktopDiscovery {
        private val mutableState = MutableStateFlow<CompanionDiscoveryState>(CompanionDiscoveryState.Idle)
        override val state: StateFlow<CompanionDiscoveryState> = mutableState

        override fun start(targetDesktopIds: Set<CompanionDesktopId>) {
            mutableState.value = CompanionDiscoveryState.Searching(targetDesktopIds.first())
        }

        override fun stop() {
            mutableState.value = CompanionDiscoveryState.Idle
        }
    }

    private class FakeDesktopAvailabilityMonitor : CompanionDesktopAvailabilityMonitor {
        private val mutableState = MutableStateFlow<CompanionDesktopAvailability>(CompanionDesktopAvailability.Idle)
        override val state: StateFlow<CompanionDesktopAvailability> = mutableState
        var executeCalls: Int = 0
        var cancellationCalls: Int = 0

        override suspend fun execute(): Nothing {
            executeCalls += 1
            try {
                awaitCancellation()
            } finally {
                cancellationCalls += 1
            }
        }

        fun publish(availability: CompanionDesktopAvailability) {
            mutableState.value = availability
        }
    }

    private class FakePairingClient : CompanionPairingClient {
        var pairedDisplayName: String? = null

        override suspend fun pair(
            invitation: CompanionPairingInvitation,
            identity: CompanionDeviceIdentity,
            displayName: String,
        ): CompanionPairingClientResult {
            pairedDisplayName = displayName
            return CompanionPairingClientResult.Paired(
                credential = "credential",
                scopes = setOf(DeviceScope.PROXY_STREAM),
                credentialExpiresAtEpochMillis = 5_000L,
            )
        }

        override suspend fun refresh(
            registration: CompanionRegistration,
            currentCredential: String,
        ): CompanionCredentialRefreshResult = CompanionCredentialRefreshResult.Refreshed("new-credential", 6_000L)
    }

    private class FakeTransport : CompanionTransport {
        private val mutableState = MutableStateFlow<CompanionConnectionState>(CompanionConnectionState.Disconnected)
        override val state: StateFlow<CompanionConnectionState> = mutableState

        override suspend fun connect(
            registration: CompanionRegistration,
            credential: String,
        ): CompanionTransportResult {
            mutableState.value = CompanionConnectionState.Connected(
                desktopId = registration.desktopId,
                transport = CompanionTransportKind.DIRECT_LAN,
                connectedAtEpochMillis = 1_000L,
            )
            return CompanionTransportResult.Connected
        }

        override suspend fun disconnect() {
            mutableState.value = CompanionConnectionState.Disconnected
        }
    }

    private class FakeInspectionController : CompanionInspectionController {
        private val mutableState = MutableStateFlow<CompanionInspectionState>(CompanionInspectionState.Stopped)
        override val state: StateFlow<CompanionInspectionState> = mutableState
        var preparation: CompanionInspectionPreparationResult = CompanionInspectionPreparationResult.Ready
        var startCalls: Int = 0

        override suspend fun prepare(): CompanionInspectionPreparationResult = preparation
        override suspend fun start(configuration: CompanionInspectionConfiguration): CompanionInspectionStartResult {
            startCalls += 1
            return CompanionInspectionStartResult.Started
        }
        override suspend fun stop() {
            mutableState.value = CompanionInspectionState.Stopped
        }
    }

    private companion object {
        const val INVITATION_SECRET = "one-time-secret!"

        fun invitation(): CompanionPairingInvitation = CompanionPairingInvitation(
            protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
            desktopId = CompanionDesktopId("desktop-1"),
            desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
            pairing = PairingInvitation(
                id = PairingInvitationId("invitation-1"),
                secret = INVITATION_SECRET,
                expiresAtEpochMillis = 5_000L,
                scopes = setOf(DeviceScope.PROXY_STREAM),
            ),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, CompanionEndpointScheme.HTTPS),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
            rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
        )

        fun bootstrap(): CompanionPairingBootstrap = CompanionPairingBootstrap(
            protocolVersion = CompanionPairingInvitation.CURRENT_PROTOCOL_VERSION,
            id = CompanionBootstrapId("bootstrap-1"),
            retrievalSecret = CompanionBootstrapSecret("r".repeat(32)),
            expiresAtEpochMillis = 5_000L,
            rootCertificateEndpoint = CompanionServiceEndpoint("192.168.1.2", 8181, CompanionEndpointScheme.HTTP),
            retrievalEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
        )

        fun registration(
            desktopId: String = "desktop-1",
            credentialReference: String = "credential-reference",
            rootFingerprint: String = "b".repeat(64),
        ): CompanionRegistration = CompanionRegistration(
            desktopId = CompanionDesktopId(desktopId),
            desktopDisplayName = CompanionDesktopDisplayName("Development Mac"),
            deviceId = RegisteredDeviceId("device-1"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, CompanionEndpointScheme.HTTPS),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, CompanionEndpointScheme.HTTPS),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint(rootFingerprint),
            rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
            credentialReference = CompanionCredentialReference(credentialReference),
            scopes = setOf(DeviceScope.PROXY_STREAM),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 5_000L,
        )
    }
}
