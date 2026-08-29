package com.devuloopers.knet.companion.connectivity.platform

import com.devuloopers.knet.companion.connectivity.bootstrap.DefaultCompanionInvitationResolver
import com.devuloopers.knet.companion.connectivity.bootstrap.KtorCompanionBootstrapClient
import com.devuloopers.knet.companion.connectivity.control.KtorCompanionControlTransport
import com.devuloopers.knet.companion.connectivity.certificate.DarwinCompanionCertificateInstallationArtifactSource
import com.devuloopers.knet.companion.connectivity.certificate.IosCertificateStoreChangeObserver
import com.devuloopers.knet.companion.connectivity.certificate.IosCompanionCertificateTrustVerifier
import com.devuloopers.knet.companion.connectivity.certificate.IosCompanionRootCertificateSource
import com.devuloopers.knet.companion.connectivity.discovery.IosCompanionDesktopDiscovery
import com.devuloopers.knet.companion.connectivity.http.IosCompanionKtorClientProvider
import com.devuloopers.knet.companion.connectivity.http.KtorCompanionHttpClient
import com.devuloopers.knet.companion.connectivity.inspection.IosCompanionInspectionController
import com.devuloopers.knet.companion.connectivity.network.IosCompanionNetworkObserver
import com.devuloopers.knet.companion.connectivity.transport.IosCompanionProxyTransport

/**
 * iOS actual factory with qualified Network.framework reachability and Security.framework TLS capabilities.
 *
 * Darwin and Security-framework TLS protect invitation redemption and control requests, while Bonjour supplies
 * identity-filtered rediscovery candidates. The certificate installation source downloads an authenticated Apple
 * configuration profile. The product supplies the controller that owns its Network Extension profile.
 */
public actual class PlatformCompanionAdapterFactory(
    private val transport: IosCompanionProxyTransport,
) : CompanionPlatformAdapterFactory {
    /** Creates an independently owned iOS adapter bundle. */
    actual override fun create(): CompanionPlatformAdapters {
        val inspectionController = IosCompanionInspectionController(transport)
        val networkObserver = IosCompanionNetworkObserver()
        val httpClient = KtorCompanionHttpClient(IosCompanionKtorClientProvider())
        val desktopDiscovery = IosCompanionDesktopDiscovery()
        val certificateStoreChanges = IosCertificateStoreChangeObserver()
        return DefaultCompanionPlatformAdapters(
            networkObserver = networkObserver,
            desktopDiscovery = desktopDiscovery,
            invitationResolver = DefaultCompanionInvitationResolver(KtorCompanionBootstrapClient(httpClient)),
            controlTransport = KtorCompanionControlTransport(httpClient),
            rootCertificateSource = IosCompanionRootCertificateSource(httpClient),
            certificateInstallationArtifactSource = DarwinCompanionCertificateInstallationArtifactSource(httpClient),
            trustVerifier = IosCompanionCertificateTrustVerifier(httpClient),
            certificateStoreChanges = certificateStoreChanges,
            inspectionController = inspectionController,
            closePlatform = {
                inspectionController.close()
                certificateStoreChanges.close()
                desktopDiscovery.stop()
                networkObserver.close()
            }
        )
    }
}
