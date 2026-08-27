package com.devuloopers.knet.companion.android.di

import com.devuloopers.knet.companion.android.inspection.AndroidInspectionRuntimeCoordinator
import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.application.usecase.*
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.connectivity.transport.AndroidTunForwarder
import com.devuloopers.knet.companion.data.ProtectedCompanionCredentialStore
import com.devuloopers.knet.companion.data.VersionedCompanionInvitationCodec
import com.devuloopers.knet.companion.data.VersionedCompanionRegistrationRepository
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
        single<CompanionCertificateTrustVerifier> { get<CompanionPlatformAdapters>().trustVerifier }
        single<CompanionCertificateStoreChangeObserver> {
            get<CompanionPlatformAdapters>().certificateStoreChanges
        }
        single<CompanionInspectionController> { get<CompanionPlatformAdapters>().inspectionController }
    }

    private fun dataBindings(bootstrap: AndroidCompanionBootstrap): Module = module {
        single<CompanionRecordStore> { bootstrap.recordStore }
        single<CompanionSecretStore> { bootstrap.secretStore }
        single<CompanionRegistrationRepository> { VersionedCompanionRegistrationRepository(get()) }
        single<CompanionCredentialStore> { ProtectedCompanionCredentialStore(get()) }
        single<CompanionInvitationCodec> { VersionedCompanionInvitationCodec() }
        single { AndroidKeystoreCompanionDeviceIdentityProvider() } bind CompanionDeviceIdentityProvider::class
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
        single { AcceptPairingInvitationUseCase(get(), get(), System::currentTimeMillis) }
        single {
            PairCompanionDeviceUseCase(get(), get(), get(), get(), System::currentTimeMillis)
        }
        single { ObserveCompanionRegistrationsUseCase(get()) }
        single { SelectCompanionRegistrationUseCase(get()) }
        single { RecoverCompanionEndpointUseCase(get(), get(), get(), get()) }
        single { MaintainCompanionEndpointUseCase(get(), get(), get(), get(), get()) }
        single { ConnectCompanionUseCase(get(), get(), get(), get(), System::currentTimeMillis, get()) }
        single { DisconnectCompanionUseCase(get()) }
        single { ObserveCompanionConnectionUseCase(get()) }
        single { ObserveCompanionNetworkUseCase(get()) }
        single { ObserveCompanionDiscoveryUseCase(get()) }
        single { DownloadCompanionRootCertificateUseCase(get(), get(), get(), System::currentTimeMillis) }
        single { VerifyCompanionCertificateTrustUseCase(get(), get(), get(), get(), System::currentTimeMillis) }
        single { ObserveCompanionCertificateStoreChangesUseCase(get()) }
        single { StartCompanionInspectionUseCase(get(), get(), get(), get(), get()) }
        single { StopCompanionInspectionUseCase(get(), get()) }
        single { ObserveCompanionInspectionUseCase(get()) }
        single { RefreshCompanionCredentialUseCase(get(), get(), get(), System::currentTimeMillis) }
        single { ForgetCompanionDesktopUseCase(get(), get(), get(), get()) }
    }

    private fun presentationBindings(): Module = module {
        single {
            CompanionViewModelDependencies(
                acceptInvitation = get(),
                pair = get(),
                observeRegistrations = get(),
                selectRegistration = get(),
                observeConnection = get(),
                observeNetwork = get(),
                observeDiscovery = get(),
                maintainEndpoint = get(),
                startInspection = get(),
                stopInspection = get(),
                observeInspection = get(),
                downloadCertificate = get(),
                verifyCertificateTrust = get(),
                observeCertificateStoreChanges = get(),
                refreshCredential = get(),
                forgetDesktop = get(),
            )
        }
        viewModel { CompanionViewModel(dependencies = get()) }
    }
}
