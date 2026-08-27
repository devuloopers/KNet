package com.devuloopers.knet.companion.connectivity.platform

import com.devuloopers.knet.companion.connectivity.bootstrap.DefaultCompanionInvitationResolver
import com.devuloopers.knet.companion.connectivity.bootstrap.KtorCompanionBootstrapClient
import com.devuloopers.knet.companion.connectivity.control.KtorCompanionControlTransport
import com.devuloopers.knet.companion.connectivity.fallback.UnavailableCompanionCertificateAdapters
import com.devuloopers.knet.companion.connectivity.fallback.UnavailableCompanionInspectionController
import com.devuloopers.knet.companion.connectivity.fallback.UnavailableCompanionNetworkObserver
import com.devuloopers.knet.companion.connectivity.discovery.IosCompanionDesktopDiscovery
import com.devuloopers.knet.companion.connectivity.http.IosCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient

/**
 * iOS actual factory with qualified Ktor bootstrap/control transport and fail-closed device capabilities.
 *
 * Darwin and Security-framework TLS protect invitation redemption and control requests, while Bonjour supplies
 * identity-filtered rediscovery candidates. Network reachability, certificate installation/readiness, and Network
 * Extension dependencies remain unavailable until their adapters are qualified by a future iOS product root.
 */
public actual class PlatformCompanionAdapterFactory : CompanionPlatformAdapterFactory {
    /** Creates an independently owned iOS bundle whose unimplemented device capabilities fail closed. */
    actual override fun create(): CompanionPlatformAdapters {
        val certificates = UnavailableCompanionCertificateAdapters(IOS_PLATFORM_NAME)
        val httpClient = KtorCompanionHttpClient(IosCompanionKtorClientProvider())
        val desktopDiscovery = IosCompanionDesktopDiscovery()
        return DefaultCompanionPlatformAdapters(
            networkObserver = UnavailableCompanionNetworkObserver(),
            desktopDiscovery = desktopDiscovery,
            invitationResolver = DefaultCompanionInvitationResolver(KtorCompanionBootstrapClient(httpClient)),
            controlTransport = KtorCompanionControlTransport(httpClient),
            rootCertificateSource = certificates.rootCertificateSource,
            trustVerifier = certificates.trustVerifier,
            certificateStoreChanges = certificates.storeChanges,
            inspectionController = UnavailableCompanionInspectionController(IOS_PLATFORM_NAME),
            closePlatform = desktopDiscovery::stop,
        )
    }

    private companion object {
        private const val IOS_PLATFORM_NAME: String = "iOS"
    }
}
