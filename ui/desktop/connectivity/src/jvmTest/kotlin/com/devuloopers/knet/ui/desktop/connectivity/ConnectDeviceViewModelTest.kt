package com.devuloopers.knet.ui.desktop.connectivity

import com.devuloopers.knet.application.contract.connectivity.wifi.WifiSharing
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.contract.proxy.ProxyRuntime
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.proxy.ProxyStartResult
import com.devuloopers.knet.application.contract.proxy.ProxyStopReason
import com.devuloopers.knet.application.contract.proxy.ProxyStopResult
import com.devuloopers.knet.application.usecase.connectivity.wifi.ObserveWifiSharingUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.proxy.StartLoopbackProxyUseCase
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
import com.devuloopers.knet.ui.desktop.connectivity.model.ConnectDeviceIntent
import com.devuloopers.knet.ui.desktop.connectivity.viewmodel.ConnectDeviceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        assertFalse(viewModel.uiState.value.isSetupDrawerVisible)

        viewModel.processIntent(ConnectDeviceIntent.OpenSetup)
        assertTrue(viewModel.uiState.value.isSetupDrawerVisible)
        viewModel.processIntent(ConnectDeviceIntent.CloseSetup)
        assertFalse(viewModel.uiState.value.isSetupDrawerVisible)
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

    private fun createViewModel(
        proxy: FakeProxyRuntime,
        wifi: FakeWifiSharingPort,
        proxyPort: Int = 8_080,
    ): ConnectDeviceViewModel = ConnectDeviceViewModel(
        startLoopbackProxy = StartLoopbackProxyUseCase(proxy),
        observeProxyRuntimeState = ObserveProxyRuntimeStateUseCase(proxy),
        observeWifiSharing = ObserveWifiSharingUseCase(wifi),
        observeApplicationSettings = ObserveApplicationSettingsUseCase(FakeApplicationSettingsRepository(proxyPort)),
    )

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

        fun activeState(): WifiSharingState.Active = WifiSharingState.Active(
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
                networkVersion = 1L,
                startedAtEpochMillis = 1_000L,
            ),
            metrics = WifiSharingMetrics(),
        )
    }
}
