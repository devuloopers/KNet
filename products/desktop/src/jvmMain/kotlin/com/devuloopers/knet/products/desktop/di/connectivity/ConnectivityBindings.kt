package com.devuloopers.knet.products.desktop.di.connectivity

import com.devuloopers.knet.application.coordinator.connectivity.ConnectivityCoordinator
import com.devuloopers.knet.application.contract.connectivity.wifi.WifiSharing
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.application.contract.pairing.RegisteredDeviceStore
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.contract.proxy.ProxyRuntime
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.usecase.pairing.PairingOnboardingEnvironment
import com.devuloopers.knet.application.usecase.pairing.PairingOnboardingEnvironmentProvider
import com.devuloopers.knet.application.usecase.pairing.CreatePairingOnboardingUseCase
import com.devuloopers.knet.application.usecase.pairing.RedeemPairingOnboardingUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.ObserveWifiSharingUseCase
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionBootstrapPayloadCodec
import com.devuloopers.knet.companion.model.CompanionBootstrapRedemptionCodec
import com.devuloopers.knet.companion.model.CompanionInvitationResponseCodec
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.connectivity.desktop.DesktopConnectivityRuntime
import com.devuloopers.knet.connectivity.desktop.adb.AdbReverseMechanism
import com.devuloopers.knet.connectivity.desktop.artifact.SetupArtifactStore
import com.devuloopers.knet.connectivity.desktop.gateway.AuthenticatedProxyGateway
import com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGateway
import com.devuloopers.knet.connectivity.desktop.gateway.IngressAttributionRegistry
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkSnapshotMonitor
import com.devuloopers.knet.connectivity.desktop.pairing.JvmPairingCrypto
import com.devuloopers.knet.connectivity.desktop.pairing.InMemoryCompanionOnboardingStore
import com.devuloopers.knet.connectivity.desktop.portal.DedicatedSetupPortal
import com.devuloopers.knet.connectivity.desktop.portal.SetupPortalContent
import com.devuloopers.knet.connectivity.desktop.provider.AdbSetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.AppleProfileSetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.ManualProxySetupProvider
import com.devuloopers.knet.connectivity.desktop.provider.PacSetupProvider
import com.devuloopers.knet.connectivity.desktop.wifi.DesktopWifiSharingRuntime
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.connectivity.spi.ManagedConnectivityMechanism
import com.devuloopers.knet.connectivity.spi.SetupDescriptorProvider
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.data.desktop.network.repository.NetworkRepositoryImpl
import com.devuloopers.knet.data.desktop.pairing.RoomRegisteredDeviceStore
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.GetLocalIpUseCase
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
import com.devuloopers.knet.engine.proxy.network.LocalIpResolver
import com.devuloopers.knet.products.desktop.connectivity.DesktopSetupPortalIndex
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
import com.devuloopers.knet.traffic.model.IngressAttributionRegistration
import com.devuloopers.knet.ui.desktop.connectivity.viewmodel.ConnectDeviceViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import java.net.InetSocketAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pairing, network discovery, setup delivery, and desktop connectivity mechanisms. */
internal val connectivityBindings: Module = module {
    single { IngressAttributionRegistry() }
    single<IngressAttributionLookup> { get<IngressAttributionRegistry>() }
    single<IngressAttributionRegistration> { get<IngressAttributionRegistry>() }

    single<PairingCryptography> { JvmPairingCrypto() }
    single { RoomRegisteredDeviceStore(get<KNetDatabase>().registeredDeviceDao()) }
    single<RegisteredDeviceStore> { get<RoomRegisteredDeviceStore>() }
    single<TrustedDeviceStore> { get<RoomRegisteredDeviceStore>() }
    single { PairingCoordinator(get(), get(), System::currentTimeMillis) }
    single<CompanionOnboardingStore> { InMemoryCompanionOnboardingStore() }
    single { CompanionBootstrapPayloadCodec() }
    single { CompanionBootstrapRedemptionCodec() }
    single { CompanionInvitationResponseCodec() }
    factory { RedeemPairingOnboardingUseCase(get(), get(), System::currentTimeMillis) }

    single { DesktopNetworkSnapshotMonitor() }
    single { DesktopConnectivityRuntime(get(), get()) }
    single { SetupArtifactStore("http://127.0.0.1:$SETUP_PORTAL_PORT") }

    single { ManualProxySetupProvider() } bind SetupDescriptorProvider::class
    single { PacSetupProvider(get()) } bind SetupDescriptorProvider::class
    single {
        val certificates: CertificateRuntimeRepository = get()
        AppleProfileSetupProvider(get()) {
            certificates.rootCertificateDer()
        }
    } bind SetupDescriptorProvider::class
    single { AdbSetupProvider() } bind SetupDescriptorProvider::class

    single {
        val proxy: ProxyRuntime = get()
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
            certificateDer = certificates::rootCertificateDer,
            onActivationFailure = { failure, cause ->
                KNetLogger.error(tag = "WifiSharing", throwable = cause) {
                    "Wi-Fi sharing activation failed: $failure. Automatic recovery remains enabled."
                }
            },
            onRecovery = { endpoint ->
                KNetLogger.info(tag = "WifiSharing") {
                    "Wi-Fi sharing recovered on ${endpoint.host}:${endpoint.port}."
                }
            },
        )
    }
    single<WifiSharing> { get<DesktopWifiSharingRuntime>() }
    factory { ObserveWifiSharingUseCase(get()) }
    single<PairingOnboardingEnvironmentProvider> {
        val certificates: CertificateRuntimeRepository = get()
        val sharing: WifiSharing = get()
        PairingOnboardingEnvironmentProvider {
            withContext(Dispatchers.IO) {
                val active = sharing.state.value as? WifiSharingState.Active
                    ?: throw IllegalStateException("A reachable local-network session is required for companion onboarding.")
                val identity = certificates.companionTlsIdentity(
                    CompanionControlGateway.TLS_SERVER_NAME,
                )
                PairingOnboardingEnvironment(
                    desktopId = CompanionDesktopId("knet-${identity.rootCertificateSha256}"),
                    desktopDisplayName = "KNet Desktop",
                    rootCertificateEndpoint = active.session.setupUrl.toRootCertificateEndpoint(),
                    controlEndpoint = CompanionServiceEndpoint(
                        host = active.session.networkAddress.address,
                        port = COMPANION_CONTROL_GATEWAY_PORT,
                        secure = true,
                    ),
                    proxyEndpoint = CompanionServiceEndpoint(
                        host = active.session.networkAddress.address,
                        port = AUTHENTICATED_GATEWAY_PORT,
                        secure = true,
                    ),
                    transportIdentitySha256 = Sha256Fingerprint(identity.transportIdentitySha256),
                    rootCertificateSha256 = Sha256Fingerprint(identity.rootCertificateSha256),
                    rootCertificate = CompanionRootCertificate(identity.copyRootCertificate()),
                )
            }
        }
    }
    factory { CreatePairingOnboardingUseCase(get(), get(), get(), get(), get()) }
    viewModel {
        ConnectDeviceViewModel(
            startLoopbackProxy = get(),
            observeProxyRuntimeState = get(),
            observeWifiSharing = get(),
            observeApplicationSettings = get(),
            createPairingOnboarding = get(),
            nowEpochMillis = System::currentTimeMillis,
        )
    }

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
                renderIndex = { _, _ -> DesktopSetupPortalIndex.render() },
                certificateDer = certificates::rootCertificateDer,
            ),
        )
    }
    single {
        val proxy: ProxyRuntime = get()
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
    single {
        val certificates: CertificateRuntimeRepository = get()
        val identity = certificates.companionTlsIdentity(
            CompanionControlGateway.TLS_SERVER_NAME,
        )
        CompanionControlGateway(
            bindHost = "0.0.0.0",
            bindPort = COMPANION_CONTROL_GATEWAY_PORT,
            serverSocketFactory = identity.serverSocketFactory,
            rootCertificateDer = identity::copyRootCertificate,
            pairing = get(),
            redeemOnboarding = get(),
            redemptionCodec = get(),
            invitationCodec = get(),
            nowEpochMillis = System::currentTimeMillis,
        )
    }
}

private const val SETUP_PORTAL_PORT: Int = 8181
private const val AUTHENTICATED_GATEWAY_PORT: Int = 8182
private const val COMPANION_CONTROL_GATEWAY_PORT: Int = 8183

/** Resolves the active Wi-Fi portal authority instead of assuming its preferred port was available. */
private fun String.toRootCertificateEndpoint(): CompanionServiceEndpoint {
    val uri = URI(this)
    require(uri.scheme == "http" && !uri.host.isNullOrBlank() && uri.port in 1..65_535) {
        "The active Wi-Fi setup URL cannot publish a companion root endpoint."
    }
    return CompanionServiceEndpoint(host = uri.host, port = uri.port, secure = false)
}
