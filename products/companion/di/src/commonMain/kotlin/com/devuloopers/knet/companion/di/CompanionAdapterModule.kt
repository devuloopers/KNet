package com.devuloopers.knet.companion.di

import com.devuloopers.knet.companion.application.contract.CompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.application.contract.CompanionCertificateStoreChangeObserver
import com.devuloopers.knet.companion.application.contract.CompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionDesktopDiscovery
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionInvitationResolver
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRootCertificateSource
import com.devuloopers.knet.companion.connectivity.platform.CompanionPlatformAdapters
import org.koin.core.module.Module
import org.koin.dsl.module

/** Exposes members of the product-owned platform aggregate through portable application contracts. */
internal fun companionAdapterModule(): Module = module {
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
    single<CompanionControlTransport> { get<CompanionPlatformAdapters>().controlTransport }
}
