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
import com.devuloopers.knet.application.usecase.pairing.CompanionDiscoveryEnvironment
import com.devuloopers.knet.application.usecase.pairing.CompanionDiscoveryEnvironmentProvider
import com.devuloopers.knet.application.usecase.pairing.CreatePairingOnboardingUseCase
import com.devuloopers.knet.application.usecase.pairing.RedeemPairingOnboardingUseCase
import com.devuloopers.knet.application.usecase.connectivity.wifi.ObserveWifiSharingUseCase
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionDesktopRuntimeId
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
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
import com.devuloopers.knet.connectivity.desktop.gateway.CompanionControlGatewayRuntime
import com.devuloopers.knet.connectivity.desktop.gateway.IngressAttributionRegistry
import com.devuloopers.knet.connectivity.desktop.discovery.CompanionDiscoveryPublisher
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
import com.devuloopers.knet.data.desktop.identity.DesktopInstallationIdentityRepository
import com.devuloopers.knet.data.desktop.network.repository.NetworkRepositoryImpl
import com.devuloopers.knet.data.desktop.pairing.RoomRegisteredDeviceStore
import com.devuloopers.knet.data.desktop.runtime.CertificateRuntimeRepository
import com.devuloopers.knet.domain.network.repository.NetworkRepository
import com.devuloopers.knet.domain.network.usecase.GetLocalIpUseCase
import com.devuloopers.knet.domain.network.usecase.ObserveLocalIpUseCase
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
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Pairing, network discovery, setup delivery, and desktop connectivity mechanisms. */
internal val connectivityBindings: Module = module {
    single { IngressAttributionRegistry() }
    single<IngressAttributionLookup> { get<IngressAttributionRegistry>() }
    single<IngressAttributionRegistration> { get<IngressAttributionRegistry>() }

    single<PairingCryptography> { JvmPairingCrypto() }
    single { RoomRegisteredDeviceStore(get<KNetDatabase>().registeredDeviceDao()) }
    single<RegisteredDeviceStore> { get<RoomRegisteredDeviceStore>() }
    single<TrustedDeviceStore> { get<RoomRegisteredDeviceStore>() }
    single { PairingCoordinator(get(), get(), ::currentEpochMillis) }
    single<CompanionOnboardingStore> { InMemoryCompanionOnboardingStore() }
    single { CompanionBootstrapPayloadCodec() }
    single { CompanionBootstrapRedemptionCodec() }
    single { CompanionInvitationResponseCodec() }
    factory { RedeemPairingOnboardingUseCase(get(), get(), ::currentEpochMillis) }

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
    single {
        CompanionDiscoveryPublisher(
            networkSnapshots = get<DesktopNetworkSnapshotMonitor>().snapshots,
            controlGatewayState = get<CompanionControlGatewayRuntime>().state,
            environmentProvider = get(),
        )
    }
    factory { ObserveWifiSharingUseCase(get()) }
    single<CompanionDiscoveryEnvironmentProvider> {
        val certificates: CertificateRuntimeRepository = get()
        val desktopIdentities: DesktopInstallationIdentityRepository = get()
        val runtimeId = CompanionDesktopRuntimeId(Uuid.random())
        CompanionDiscoveryEnvironmentProvider {
            val tlsIdentity = certificates.companionTlsIdentity(CompanionControlGateway.TLS_SERVER_NAME)
            val identity = desktopIdentities.loadOrCreate(
                setOf(CompanionDesktopId("knet-${tlsIdentity.rootCertificateSha256}")),
            )
            CompanionDiscoveryEnvironment(
                desktopId = identity.canonicalId,
                legacyDesktopIds = identity.legacyIds,
                runtimeId = runtimeId,
                controlPort = COMPANION_CONTROL_GATEWAY_PORT,
                proxyPort = AUTHENTICATED_GATEWAY_PORT,
            )
        }
    }
    single<PairingOnboardingEnvironmentProvider> {
        val certificates: CertificateRuntimeRepository = get()
        val discovery: CompanionDiscoveryEnvironmentProvider = get()
        val sharing: WifiSharing = get()
        PairingOnboardingEnvironmentProvider {
            withContext(Dispatchers.IO) {
                val active = sharing.state.value as? WifiSharingState.Active
                    ?: throw IllegalStateException("A reachable local-network session is required for companion onboarding.")
                val identity = certificates.companionTlsIdentity(
                    CompanionControlGateway.TLS_SERVER_NAME,
                )
                val desktopIdentity = discovery.load()
                PairingOnboardingEnvironment(
                    desktopId = desktopIdentity.desktopId,
                    desktopDisplayName = CompanionDesktopDisplayName("KNet Desktop"),
                    rootCertificateEndpoint = active.session.setupUrl.toRootCertificateEndpoint(),
                    controlEndpoint = CompanionServiceEndpoint(
                        host = active.session.networkAddress.address,
                        port = COMPANION_CONTROL_GATEWAY_PORT,
                        scheme = CompanionEndpointScheme.HTTPS,
                    ),
                    proxyEndpoint = CompanionServiceEndpoint(
                        host = active.session.networkAddress.address,
                        port = AUTHENTICATED_GATEWAY_PORT,
                        scheme = CompanionEndpointScheme.HTTPS,
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
            nowEpochMillis = ::currentEpochMillis,
        )
    }

    single<NetworkRepository> { NetworkRepositoryImpl(get<DesktopNetworkSnapshotMonitor>().snapshots) }
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
        val certificates: CertificateRuntimeRepository = get()
        val identity = certificates.companionTlsIdentity(
            CompanionControlGateway.TLS_SERVER_NAME,
        )
        AuthenticatedProxyGateway(
            bindHost = COMPANION_LAN_BIND_HOST,
            bindPort = AUTHENTICATED_GATEWAY_PORT,
            serverSocketFactory = identity.serverSocketFactory,
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
        val discovery: CompanionDiscoveryEnvironmentProvider = get()
        CompanionControlGateway(
            bindHost = COMPANION_LAN_BIND_HOST,
            bindPort = COMPANION_CONTROL_GATEWAY_PORT,
            serverSocketFactory = identity.serverSocketFactory,
            rootCertificateDer = identity::copyRootCertificate,
            pairing = get(),
            redeemOnboarding = get(),
            redemptionCodec = get(),
            invitationCodec = get(),
            endpointDescriptor = { discovery.load().endpointDescriptor() },
            nowEpochMillis = ::currentEpochMillis,
        )
    }
    single { CompanionControlGatewayRuntime(get<CompanionControlGateway>()) }
}

private const val SETUP_PORTAL_PORT: Int = 8181
internal const val COMPANION_LAN_BIND_HOST: String = "0.0.0.0"
private const val AUTHENTICATED_GATEWAY_PORT: Int = 8182
private const val COMPANION_CONTROL_GATEWAY_PORT: Int = 8183

private fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Resolves the active Wi-Fi portal authority instead of assuming its preferred port was available. */
private fun String.toRootCertificateEndpoint(): CompanionServiceEndpoint {
    val uri = URI(this)
    require(uri.scheme == "http" && !uri.host.isNullOrBlank() && uri.port in 1..65_535) {
        "The active Wi-Fi setup URL cannot publish a companion root endpoint."
    }
    return CompanionServiceEndpoint(host = uri.host, port = uri.port, scheme = CompanionEndpointScheme.HTTP)
}
