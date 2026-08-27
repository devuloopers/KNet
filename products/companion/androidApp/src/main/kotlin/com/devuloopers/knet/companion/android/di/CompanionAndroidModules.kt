package com.devuloopers.knet.companion.android.di

import com.devuloopers.knet.companion.android.inspection.AndroidInspectionRuntimeCoordinator
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionPairingClient
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.application.usecase.AcceptPairingInvitationUseCase
import com.devuloopers.knet.companion.application.usecase.ConnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DisconnectCompanionUseCase
import com.devuloopers.knet.companion.application.usecase.DownloadCompanionRootCertificateUseCase
import com.devuloopers.knet.companion.application.usecase.ForgetCompanionDesktopUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionCertificateStoreChangesUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionConnectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionNetworkUseCase
import com.devuloopers.knet.companion.application.usecase.ObserveCompanionRegistrationsUseCase
import com.devuloopers.knet.companion.application.usecase.PairCompanionDeviceUseCase
import com.devuloopers.knet.companion.application.usecase.RefreshCompanionCredentialUseCase
import com.devuloopers.knet.companion.application.usecase.SelectCompanionRegistrationUseCase
import com.devuloopers.knet.companion.application.usecase.StartCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.StopCompanionInspectionUseCase
import com.devuloopers.knet.companion.application.usecase.VerifyCompanionCertificateTrustUseCase
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import com.devuloopers.knet.companion.connectivity.transport.AndroidTunForwarder
import com.devuloopers.knet.companion.data.control.DefaultCompanionPairingClient
import com.devuloopers.knet.companion.data.ProtectedCompanionCredentialStore
import com.devuloopers.knet.companion.data.VersionedCompanionInvitationCodec
import com.devuloopers.knet.companion.data.VersionedCompanionRegistrationRepository
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceProofSigner
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
        single { ConnectCompanionUseCase(get(), get(), get(), get(), System::currentTimeMillis) }
        single { DisconnectCompanionUseCase(get()) }
        single { ObserveCompanionConnectionUseCase(get()) }
        single { ObserveCompanionNetworkUseCase(get()) }
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
