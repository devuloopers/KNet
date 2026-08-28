package com.devuloopers.knet.companion.android.di

import com.devuloopers.knet.companion.android.inspection.AndroidInspectionRuntimeCoordinator
import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.application.contract.CompanionCertificateEnrollmentRepository
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.application.contract.CompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationUseCase
import com.devuloopers.knet.companion.application.usecase.CompanionDesktopAvailabilityMonitor
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.CompleteCompanionCertificateEnrollmentUseCase
import com.devuloopers.knet.companion.application.usecase.DisconnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateUseCase
import com.devuloopers.knet.companion.application.usecase.ForgetCompanionDesktopUseCase
import com.devuloopers.knet.companion.application.usecase.MaintainCompanionEndpointUseCase
import com.devuloopers.knet.companion.application.usecase.MonitorCompanionDesktopAvailabilityUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateStoreChangesUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateEnrollmentsUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionConnectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionDiscoveryUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionNetworkUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionRegistrationsUseCase
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceUseCase
import com.devuloopers.knet.companion.application.usecase.RecoverCompanionEndpointUseCase
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialUseCase
import com.devuloopers.knet.companion.application.usecase.SelectCompanionRegistrationUseCase
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.StopCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.VerifyCompanionCertificateTrustUseCase
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.connectivity.transport.AndroidTunForwarder
import com.devuloopers.knet.companion.data.ProtectedCompanionCredentialStore
import com.devuloopers.knet.companion.data.VersionedCompanionInvitationCodec
import com.devuloopers.knet.companion.data.VersionedCompanionStateRepository
import com.devuloopers.knet.companion.data.android.AndroidCompanionDeviceDisplayNameProvider
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceProofSigner
import com.devuloopers.knet.companion.data.control.DefaultCompanionEndpointReconciliationClient
import com.devuloopers.knet.companion.data.control.DefaultCompanionPairingClient
import com.devuloopers.knet.companion.data.store.CompanionRecordStore
import com.devuloopers.knet.companion.data.store.CompanionSecretStore
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModel
import com.devuloopers.knet.companion.presentation.viewmodel.CompanionViewModelDependencies
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.time.Clock

/** Product-owned Koin module set for the Android companion application. */
internal object CompanionAndroidModules {
    /** Creates a fresh, non-overlapping module set around one restored Android bootstrap. */
    fun create(bootstrap: AndroidCompanionBootstrap): List<Module> = listOf(
        platformBindings(bootstrap),
        dataBindings(bootstrap),
        runtimeBindings(bootstrap),
        applicationBindings(),
        presentationBindings(),
    )

    private fun platformBindings(bootstrap: AndroidCompanionBootstrap): Module = module {
        single<CompanionPlatformAdapters>(createdAtStart = true) { bootstrap.platformAdapters } onClose { adapters ->
            adapters?.close()
        }
        single<CompanionNetworkObserver> { get<CompanionPlatformAdapters>().networkObserver }
        single<CompanionDesktopDiscovery> { get<CompanionPlatformAdapters>().desktopDiscovery }
        single<CompanionInvitationResolver> { get<CompanionPlatformAdapters>().invitationResolver }
        single<CompanionRootCertificateSource> { get<CompanionPlatformAdapters>().rootCertificateSource }
        single<CompanionCertificateInstallationArtifactSource> {
            get<CompanionPlatformAdapters>().certificateInstallationArtifactSource
        }
        single<CompanionCertificateTrustVerifier> { get<CompanionPlatformAdapters>().trustVerifier }
        single<CompanionCertificateStoreChangeObserver> {
            get<CompanionPlatformAdapters>().certificateStoreChanges
        }
        single<CompanionInspectionController> { get<CompanionPlatformAdapters>().inspectionController }
    }

    private fun dataBindings(bootstrap: AndroidCompanionBootstrap): Module = module {
        single<CompanionRecordStore> { bootstrap.recordStore }
        single<CompanionSecretStore> { bootstrap.secretStore }
        single { VersionedCompanionStateRepository(get()) }
        single<CompanionRegistrationRepository> { get<VersionedCompanionStateRepository>() }
        single<CompanionCertificateEnrollmentRepository> { get<VersionedCompanionStateRepository>() }
        single<CompanionCredentialStore> { ProtectedCompanionCredentialStore(get()) }
        single<CompanionInvitationCodec> { VersionedCompanionInvitationCodec() }
        single { AndroidKeystoreCompanionDeviceIdentityProvider() } bind CompanionDeviceIdentityProvider::class
        single { AndroidCompanionDeviceDisplayNameProvider() } bind CompanionDeviceDisplayNameProvider::class
        single { AndroidKeystoreCompanionDeviceProofSigner() } bind CompanionDeviceProofSigner::class
    }

    private fun runtimeBindings(bootstrap: AndroidCompanionBootstrap): Module = module {
        single<CompanionControlTransport> { get<CompanionPlatformAdapters>().controlTransport }
        single<CompanionPairingClient> { DefaultCompanionPairingClient(get(), get()) }
        single<CompanionEndpointReconciliationClient> { DefaultCompanionEndpointReconciliationClient(get()) }
        single<CompanionTransport> { bootstrap.transport }
        single<AndroidTunForwarder> { bootstrap.tunForwarder }
        single<AndroidInspectionRuntimeCoordinator> { bootstrap.inspectionCoordinator }
    }

    private fun applicationBindings(): Module = module {
        single { AcceptPairingInvitationUseCase(get(), get(), ::currentEpochMillis) }
        single {
            PairCompanionDeviceUseCase(get(), get(), get(), get(), get(), ::currentEpochMillis)
        }
        single { ObserveCompanionRegistrationsUseCase(get()) }
        single { ObserveCompanionCertificateEnrollmentsUseCase(get()) }
        single { SelectCompanionRegistrationUseCase(get()) }
        single { RecoverCompanionEndpointUseCase(get(), get(), get(), get()) }
        single<CompanionDesktopAvailabilityMonitor> {
            MonitorCompanionDesktopAvailabilityUseCase(get(), get(), get(), get(), get(), ::currentEpochMillis)
        }
        single { MaintainCompanionEndpointUseCase(get(), get(), get(), get(), get()) }
        single { ConnectCompanionUseCase(get(), get(), get(), get(), ::currentEpochMillis, get()) }
        single { DisconnectCompanionUseCase(get()) }
        single { ObserveCompanionConnectionUseCase(get()) }
        single { ObserveCompanionNetworkUseCase(get()) }
        single { ObserveCompanionDiscoveryUseCase(get()) }
        single { DownloadCompanionRootCertificateUseCase(get(), get(), get(), ::currentEpochMillis) }
        single { VerifyCompanionCertificateTrustUseCase(get(), get(), get(), get(), ::currentEpochMillis) }
        single { CompleteCompanionCertificateEnrollmentUseCase(get(), get(), get(), ::currentEpochMillis) }
        single { ObserveCompanionCertificateStoreChangesUseCase(get()) }
        single { StartCompanionInspectionUseCase(get(), get(), get(), get(), get()) }
        single { StopCompanionInspectionUseCase(get(), get()) }
        single { ObserveCompanionInspectionUseCase(get()) }
        single { RefreshCompanionCredentialUseCase(get(), get(), get(), ::currentEpochMillis) }
        single { ForgetCompanionDesktopUseCase(get(), get(), get(), get(), get()) }
    }

    private fun presentationBindings(): Module = module {
        single {
            CompanionViewModelDependencies(
                acceptInvitation = get(),
                pair = get(),
                observeRegistrations = get(),
                observeCertificateEnrollments = get(),
                selectRegistration = get(),
                observeConnection = get(),
                observeNetwork = get(),
                observeDiscovery = get(),
                maintainEndpoint = get(),
                monitorDesktopAvailability = get(),
                startInspection = get(),
                stopInspection = get(),
                observeInspection = get(),
                downloadCertificate = get(),
                verifyCertificateTrust = get(),
                completeCertificateEnrollment = get(),
                observeCertificateStoreChanges = get(),
                refreshCredential = get(),
                forgetDesktop = get(),
            )
        }
        viewModel { CompanionViewModel(dependencies = get()) }
    }
}

private fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
