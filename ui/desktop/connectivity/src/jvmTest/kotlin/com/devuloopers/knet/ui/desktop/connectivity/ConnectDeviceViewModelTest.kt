package com.devuloopers.knet.ui.desktop.connectivity

import com.devuloopers.knet.companion.model.CompanionDesktopDisplayName
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import com.devuloopers.knet.application.contract.connectivity.wifi.WifiSharing
import com.devuloopers.knet.application.contract.pairing.PairingCryptography
import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.application.contract.pairing.PendingCompanionOnboarding
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.contract.proxy.ProxyRuntime
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.proxy.ProxyStartResult
import com.devuloopers.knet.application.contract.proxy.ProxyStopReason
import com.devuloopers.knet.application.contract.proxy.ProxyStopResult
import com.devuloopers.knet.application.usecase.connectivity.wifi.ObserveWifiSharingUseCase
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.application.usecase.pairing.CreatePairingOnboardingUseCase
import com.devuloopers.knet.application.usecase.pairing.PairingOnboardingEnvironment
import com.devuloopers.knet.application.usecase.pairing.PairingOnboardingEnvironmentProvider
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionBootstrapPayloadCodec
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.connectivity.model.NetworkAddress
import com.devuloopers.knet.connectivity.model.NetworkAddressFamily
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.connectivity.model.WifiSharingMetrics
import com.devuloopers.knet.connectivity.model.WifiSharingSession
import com.devuloopers.knet.connectivity.model.WifiSharingSessionId
import com.devuloopers.knet.connectivity.model.WifiSharingState
import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.model.ProxyPort
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceProofAlgorithm
import com.devuloopers.knet.pairing.PairingInvitationId
import com.devuloopers.knet.pairing.PendingPairingInvitation
import com.devuloopers.knet.pairing.TrustedDevice
import com.devuloopers.knet.ui.desktop.connectivity.model.CompanionInvitationUiState
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent
import com.devuloopers.knet.ui.desktop.connectivity.viewmodel.ConnectDeviceViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectDeviceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state uses saved proxy port and opens one setup drawer`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeProxyRuntime(), FakeWifiSharingPort(), proxyPort = 9_090)
        runCurrent()

        assertEquals(9_090, viewModel.uiState.value.preferredProxyPort)
        assertFalse(viewModel.uiState.value.isWifiSetupDrawerVisible)

        viewModel.processIntent(ConnectDeviceIntent.OpenWifiSetup)
        assertTrue(viewModel.uiState.value.isWifiSetupDrawerVisible)
        viewModel.processIntent(ConnectDeviceIntent.CloseDrawer)
        assertFalse(viewModel.uiState.value.isWifiSetupDrawerVisible)
    }

    @Test
    fun `start proxy uses saved port while Wi-Fi lifecycle remains runtime owned`() = runTest(dispatcher) {
        val proxy = FakeProxyRuntime()
        val wifi = FakeWifiSharingPort()
        val viewModel = createViewModel(proxy, wifi, proxyPort = 9_090)
        runCurrent()

        viewModel.processIntent(ConnectDeviceIntent.StartProxy)
        runCurrent()

        assertEquals(9_090, proxy.startedPort)
        assertIs<ProxyRuntimeState.Running>(viewModel.uiState.value.proxyState)
        assertIs<WifiSharingState.Disabled>(wifi.state.value)
    }

    @Test
    fun `proxy start failure remains available for inline presentation`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            proxy = FakeProxyRuntime(startFailureCode = "proxy_bind_failed"),
            wifi = FakeWifiSharingPort(),
        )
        runCurrent()

        viewModel.processIntent(ConnectDeviceIntent.StartProxy)
        runCurrent()

        assertEquals("proxy_bind_failed", viewModel.uiState.value.failureCode)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `active automatic Wi-Fi state reaches presentation unchanged`() = runTest(dispatcher) {
        val wifi = FakeWifiSharingPort()
        val viewModel = createViewModel(FakeProxyRuntime(runningPort = 8_080), wifi)
        runCurrent()
        val active = activeState()

        wifi.mutableState.value = active
        runCurrent()

        assertEquals(active, viewModel.uiState.value.activeSharing)
        assertEquals("http://192.0.2.10:8181/setup", viewModel.uiState.value.activeSharing?.session?.setupUrl)
    }

    @Test
    fun `opening companion drawer creates version two QR and closing removes secret from state`() = runTest(dispatcher) {
        val wifi = FakeWifiSharingPort().apply { mutableState.value = activeState() }
        val viewModel = createViewModel(FakeProxyRuntime(runningPort = 8_080), wifi)
        runCurrent()

        viewModel.processIntent(ConnectDeviceIntent.OpenCompanionConnection)
        runCurrent()

        val ready = assertIs<CompanionInvitationUiState.Ready>(viewModel.uiState.value.companionInvitation)
        assertTrue(ready.qrPayload.startsWith("knet://pair/v3?"))
        assertEquals(IPV4_ADDRESS.address, ready.host)

        viewModel.processIntent(ConnectDeviceIntent.CloseDrawer)

        assertIs<CompanionInvitationUiState.Idle>(viewModel.uiState.value.companionInvitation)
        assertFalse(viewModel.uiState.value.isCompanionDrawerVisible)
    }

    @Test
    fun `refresh replaces invitation and expiry removes QR payload`() = runTest(dispatcher) {
        var nowMillis = 1_000L
        val wifi = FakeWifiSharingPort().apply { mutableState.value = activeState() }
        val viewModel = createViewModel(
            proxy = FakeProxyRuntime(runningPort = 8_080),
            wifi = wifi,
            nowEpochMillis = { nowMillis },
        )
        runCurrent()
        viewModel.processIntent(ConnectDeviceIntent.OpenCompanionConnection)
        runCurrent()
        val first = assertIs<CompanionInvitationUiState.Ready>(viewModel.uiState.value.companionInvitation)

        viewModel.processIntent(ConnectDeviceIntent.RefreshCompanionInvitation)
        runCurrent()
        val refreshed = assertIs<CompanionInvitationUiState.Ready>(viewModel.uiState.value.companionInvitation)

        assertTrue(first.qrPayload != refreshed.qrPayload)
        nowMillis = refreshed.expiresAtEpochMillis
        advanceTimeBy(1_000L)
        runCurrent()
        assertIs<CompanionInvitationUiState.Expired>(viewModel.uiState.value.companionInvitation)
    }

    @Test
    fun `network change during creation replaces stale job instead of leaving creating state`() = runTest(dispatcher) {
        val environmentGate = CompletableDeferred<Unit>()
        val wifi = FakeWifiSharingPort().apply { mutableState.value = activeState(networkVersion = 1L) }
        val viewModel = createViewModel(
            proxy = FakeProxyRuntime(runningPort = 8_080),
            wifi = wifi,
            environmentGate = environmentGate,
        )
        runCurrent()

        viewModel.processIntent(ConnectDeviceIntent.OpenCompanionConnection)
        runCurrent()
        assertIs<CompanionInvitationUiState.Creating>(viewModel.uiState.value.companionInvitation)

        wifi.mutableState.value = activeState(networkVersion = 2L)
        runCurrent()
        environmentGate.complete(Unit)
        runCurrent()

        val ready = assertIs<CompanionInvitationUiState.Ready>(viewModel.uiState.value.companionInvitation)
        assertEquals(2L, ready.networkVersion)
        viewModel.processIntent(ConnectDeviceIntent.CloseDrawer)
    }

    private fun createViewModel(
        proxy: FakeProxyRuntime,
        wifi: FakeWifiSharingPort,
        proxyPort: Int = 8_080,
        nowEpochMillis: () -> Long = { 1_000L },
        environmentGate: CompletableDeferred<Unit>? = null,
    ): ConnectDeviceViewModel {
        val cryptography = SequentialPairingCryptography()
        return ConnectDeviceViewModel(
            startLoopbackProxy = StartLoopbackProxyUseCase(proxy),
            observeProxyRuntimeState = ObserveProxyRuntimeStateUseCase(proxy),
            observeWifiSharing = ObserveWifiSharingUseCase(wifi),
            observeApplicationSettings = ObserveApplicationSettingsUseCase(FakeApplicationSettingsRepository(proxyPort)),
            createPairingOnboarding = CreatePairingOnboardingUseCase(
                pairing = PairingCoordinator(
                    store = MemoryTrustedDeviceStore(),
                    crypto = cryptography,
                    nowMillis = nowEpochMillis,
                ),
                environmentProvider = PairingOnboardingEnvironmentProvider {
                    environmentGate?.await()
                    val active = assertIs<WifiSharingState.Active>(wifi.state.value)
                    PairingOnboardingEnvironment(
                        desktopId = CompanionDesktopId("desktop-1"),
                        desktopDisplayName = CompanionDesktopDisplayName("KNet Desktop"),
                        rootCertificateEndpoint = CompanionServiceEndpoint(
                            active.session.networkAddress.address,
                            8_181,
                            false,
                        ),
                        controlEndpoint = CompanionServiceEndpoint(active.session.networkAddress.address, 8_183, CompanionEndpointScheme.HTTPS),
                        proxyEndpoint = CompanionServiceEndpoint(active.session.networkAddress.address, 8_182, CompanionEndpointScheme.HTTPS),
                        transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
                        rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
                        rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
                    )
                },
                cryptography = cryptography,
                onboardingStore = MemoryCompanionOnboardingStore(),
                payloadCodec = CompanionBootstrapPayloadCodec(),
            ),
            nowEpochMillis = nowEpochMillis,
        )
    }

    private class FakeProxyRuntime(
        runningPort: Int? = null,
        private val startFailureCode: String? = null,
    ) : ProxyRuntime {
        private val mutableState = MutableStateFlow<ProxyRuntimeState>(
            runningPort?.let(::runningState) ?: ProxyRuntimeState.Stopped,
        )
        override val state: StateFlow<ProxyRuntimeState> = mutableState
        var startedPort: Int? = null

        override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult {
            startedPort = configuration.bindings.single().port
            startFailureCode?.let { code -> return ProxyStartResult.Failed(code) }
            val running = runningState(requireNotNull(startedPort))
            mutableState.value = running
            return ProxyStartResult.Running(running.handle)
        }

        override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
            mutableState.value = ProxyRuntimeState.Stopped
            return ProxyStopResult.Stopped
        }
    }

    private class FakeWifiSharingPort : WifiSharing {
        val mutableState = MutableStateFlow<WifiSharingState>(WifiSharingState.Disabled(listOf(IPV4_ADDRESS)))
        override val state: StateFlow<WifiSharingState> = mutableState
    }

    private class FakeApplicationSettingsRepository(proxyPort: Int) : ApplicationSettingsRepository {
        override val settings: Flow<ApplicationSettings> =
            MutableStateFlow(ApplicationSettings(proxyPort = ProxyPort(proxyPort)))

        override suspend fun update(transform: (ApplicationSettings) -> ApplicationSettings) = Unit
    }

    private class MemoryTrustedDeviceStore : TrustedDeviceStore {
        override suspend fun putInvitation(invitation: PendingPairingInvitation) = Unit

        override suspend fun claimInvitation(
            id: PairingInvitationId,
            secretDigest: String,
            nowEpochMillis: Long,
        ): PendingPairingInvitation? = null

        override suspend fun putDevice(device: TrustedDevice) = Unit

        override suspend fun getDevice(id: RegisteredDeviceId): TrustedDevice? = null

        override suspend fun rotateCredential(
            id: RegisteredDeviceId,
            expectedCredentialDigest: String,
            newCredentialDigest: String,
            credentialExpiresAtEpochMillis: Long,
        ): Boolean = false

        override suspend fun revoke(id: RegisteredDeviceId, revokedAtEpochMillis: Long): Boolean = false

        override fun observeDevices(): Flow<List<TrustedDevice>> = emptyFlow()
    }

    private class MemoryCompanionOnboardingStore : CompanionOnboardingStore {
        override suspend fun put(pending: PendingCompanionOnboarding) = Unit

        override suspend fun claim(
            id: CompanionBootstrapId,
            retrievalSecretDigest: String,
            nowEpochMillis: Long,
        ): CompanionPairingInvitation? = null
    }

    private class SequentialPairingCryptography : PairingCryptography {
        private var sequence: Int = 0

        override fun randomToken(entropyBytes: Int): String {
            sequence += 1
            return "token-$sequence".padEnd(entropyBytes.coerceAtLeast(16), 'x')
        }

        override fun digest(value: String): String = "digest-${value.length}-${value.first()}"

        override fun constantTimeMatches(value: String, expectedDigest: String): Boolean =
            digest(value) == expectedDigest

        override fun verifyDeviceProof(
            algorithm: DeviceProofAlgorithm,
            publicKeyEncoded: String,
            message: String,
            signatureEncoded: String,
        ): Boolean = true
    }

    private companion object {
        val IPV4_ADDRESS = NetworkAddress("en0", "192.0.2.10", NetworkAddressFamily.IPV4, loopback = false)

        fun runningState(port: Int): ProxyRuntimeState.Running = ProxyRuntimeState.Running(
            ProxyRuntimeHandle(
                runtimeId = "proxy-test",
                endpoints = ProxyEndpointSnapshot(
                    ProxyEndpointVersion(1L),
                    listOf(
                        ProxyEndpoint(
                            host = "127.0.0.1",
                            port = port,
                            scope = ProxyEndpointScope.LOOPBACK,
                            accessRequirement = ProxyAccessRequirement.LOCAL_PROCESS,
                        ),
                    ),
                ),
            ),
        )

        fun activeState(networkVersion: Long = 1L): WifiSharingState.Active = WifiSharingState.Active(
            session = WifiSharingSession(
                id = WifiSharingSessionId("sharing-test"),
                networkAddress = IPV4_ADDRESS,
                proxyEndpoint = ProxyEndpoint(
                    host = IPV4_ADDRESS.address,
                    port = 8_080,
                    scope = ProxyEndpointScope.LAN,
                    accessRequirement = ProxyAccessRequirement.OPEN_LAN_CLIENT,
                ),
                setupUrl = "http://${IPV4_ADDRESS.address}:8181/setup",
                certificateSha256 = "a".repeat(64),
                networkVersion = networkVersion,
                startedAtEpochMillis = 1_000L,
            ),
            metrics = WifiSharingMetrics(),
        )
    }
}
