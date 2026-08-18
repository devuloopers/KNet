package com.devuloopers.knet.products.desktop.di.connectivity

import com.devuloopers.knet.application.port.connectivity.ConnectivityCoordinator
import com.devuloopers.knet.application.port.connectivity.wifi.WifiSharingPort
import com.devuloopers.knet.application.port.pairing.PairingCoordinator
import com.devuloopers.knet.application.port.pairing.PairingCryptoPort
import com.devuloopers.knet.application.port.pairing.TrustedDeviceStorePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.usecase.pairing.CreatePairingOnboardingUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.ApproveWifiClientUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.CreateWifiInvitationUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.DisableWifiSharingUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.EnableWifiSharingUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.ObserveWifiSharingUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.RejectWifiClientUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.RevokeWifiClientUseCase
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.desktop.adb.AdbReverseMechanism
import com.devuloopers.knet.connectivity.desktop.artifact.SetupArtifactStore
import com.devuloopers.knet.connectivity.desktop.gateway.AuthenticatedProxyGateway
import com.devuloopers.knet.connectivity.desktop.gateway.IngressAttributionRegistry
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkSnapshotMonitor
import com.devuloopers.knet.connectivity.desktop.pairing.EncryptedFileTrustedDeviceStore
import com.devuloopers.knet.connectivity.desktop.pairing.JvmPairingCrypto
import com.devuloopers.knet.connectivity.desktop.portal.DedicatedSetupPortal
import com.devuloopers.knet.connectivity.desktop.portal.SetupPortalContent
import com.devuloopers.knet.connectivity.desktop.provider.AdbSetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.AppleProfileSetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.ManualProxySetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.PacSetupProvider
import com.devuloopers.knet.connectivity.desktop.wifi.DesktopWifiSharingRuntime
import com.devuloopers.knet.connectivity.spi.ManagedConnectivityMechanism
import com.devuloopers.knet.connectivity.spi.SetupDescriptorProvider
import com.devuloopers.knet.data.desktop.network.repository.NetworkRepositoryImpl
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.GetLocalIpUseCase
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.engine.proxy.network.LocalIpResolver
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
import com.devuloopers.knet.traffic.model.IngressAttributionRegistration
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File
import java.net.InetSocketAddress

/** Pairing, network discovery, setup delivery, and desktop connectivity mechanisms. */
internal val connectivityBindings: Module = module {
    single { IngressAttributionRegistry() }
    single<IngressAttributionLookup> { get<IngressAttributionRegistry>() }
    single<IngressAttributionRegistration> { get<IngressAttributionRegistry>() }

    single<PairingCryptoPort> { JvmPairingCrypto() }
    single<TrustedDeviceStorePort> {
        EncryptedFileTrustedDeviceStore(
            File(System.getProperty("user.home"), ".knet/pairing").toPath(),
        )
    }
    single { PairingCoordinator(get(), get(), System::currentTimeMillis) }
    factory { CreatePairingOnboardingUseCase(get()) }

    single { DesktopNetworkSnapshotMonitor() }
    single { DesktopConnectivityRuntime(get(), get()) }
    single { SetupArtifactStore("http://127.0.0.1:$SETUP_PORTAL_PORT") }

    single { ManualProxySetupProvider() } bind SetupDescriptorProvider::class
    single { PacSetupProvider(get()) } bind SetupDescriptorProvider::class
    single {
        val certificates: CertificateRuntimeRepository = get()
        AppleProfileSetupProvider(get()) {
            certificates.certificateAuthority.certificate.encoded
        }
    } bind SetupDescriptorProvider::class
    single { AdbSetupProvider() } bind SetupDescriptorProvider::class

    single {
        val proxy: ProxyRuntimePort = get()
        AdbReverseMechanism(proxyPort = {
            (proxy.state.value as? ProxyRuntimeState.Running)
                ?.handle?.endpoints?.endpoints?.firstOrNull()?.port
        })
    } bind ManagedConnectivityMechanism::class
    single { ConnectivityCoordinator(getAll(), getAll()) }
    single {
        val certificates: CertificateRuntimeRepository = get()
        DesktopWifiSharingRuntime(
            proxyRuntime = get(),
            connectivityRuntime = get(),
            attributions = get<IngressAttributionRegistration>(),
            certificateDer = { certificates.certificateAuthority.certificate.encoded },
        )
    }
    single<WifiSharingPort> { get<DesktopWifiSharingRuntime>() }
    factory { EnableWifiSharingUseCase(get()) }
    factory { DisableWifiSharingUseCase(get()) }
    factory { ObserveWifiSharingUseCase(get()) }
    factory { CreateWifiInvitationUseCase(get()) }
    factory { ApproveWifiClientUseCase(get()) }
    factory { RejectWifiClientUseCase(get()) }
    factory { RevokeWifiClientUseCase(get()) }

    single { LocalIpResolver() }
    single<NetworkRepository> { NetworkRepositoryImpl(get()) }
    factory { ObserveLocalIpUseCase(get()) }
    factory { GetLocalIpUseCase(get()) }

    single {
        val certificates: CertificateRuntimeRepository = get()
        DedicatedSetupPortal(
            bindHost = "127.0.0.1",
            port = SETUP_PORTAL_PORT,
            allowedAuthorities = setOf("knet.local"),
            artifacts = get(),
            content = SetupPortalContent(
                renderIndex = { _, _ ->
                    """<!doctype html><html><head><meta charset="utf-8"><title>KNet Setup</title></head><body><h1>KNet Setup</h1><p>Choose a registered connectivity mechanism in KNet.</p><a href="/knet-ca.crt">Install KNet CA</a></body></html>"""
                },
                certificateDer = { certificates.certificateAuthority.certificate.encoded },
            ),
        )
    }
    single {
        val proxy: ProxyRuntimePort = get()
        AuthenticatedProxyGateway(
            bindPort = AUTHENTICATED_GATEWAY_PORT,
            targetProxy = {
                (proxy.state.value as? ProxyRuntimeState.Running)
                    ?.handle?.endpoints?.endpoints?.firstOrNull()
                    ?.let { InetSocketAddress(it.host, it.port) }
            },
            pairing = get(),
            attributions = get(),
        )
    }
}

private const val SETUP_PORTAL_PORT: Int = 8181
private const val AUTHENTICATED_GATEWAY_PORT: Int = 8182
