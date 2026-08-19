package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyStopResult
import com.devuloopers.knet.application.port.traffic.CaptureClearPreparation
import com.devuloopers.knet.application.port.traffic.CapturePauseResult
import com.devuloopers.knet.application.port.traffic.CaptureResumeResult
import com.devuloopers.knet.application.port.traffic.CaptureSessionControlPort
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficCaptureLifecycleTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `toolbar stop pauses capture and next start reuses proxy runtime`() = runTest(dispatcher) {
        val runtime = StatefulCaptureRuntime()
        val viewModel = FakeTrafficViewModelFactory.create(
            customProxyRuntime = runtime,
            customCaptureSessionControl = runtime,
        )
        advanceUntilIdle()
        val initialPauseCalls = runtime.pauseCalls

        viewModel.processIntent(TrafficIntent.StartCapture)
        advanceUntilIdle()
        assertEquals(1, runtime.startCalls)
        assertIs<ProxyRuntimeState.Running>(runtime.state.value)
        assertIs<CaptureSessionState.Capturing>(runtime.captureState.value)

        viewModel.processIntent(TrafficIntent.StopCapture)
        advanceUntilIdle()
        assertEquals(initialPauseCalls + 1, runtime.pauseCalls)
        assertEquals(0, runtime.stopCalls)
        assertIs<ProxyRuntimeState.Running>(runtime.state.value)
        assertIs<CaptureSessionState.Paused>(runtime.captureState.value)

        viewModel.processIntent(TrafficIntent.StartCapture)
        advanceUntilIdle()
        assertEquals(1, runtime.startCalls)
        assertEquals(1, runtime.resumeCalls)
        assertEquals(0, runtime.stopCalls)
        assertIs<ProxyRuntimeState.Running>(runtime.state.value)
        assertIs<CaptureSessionState.Capturing>(runtime.captureState.value)
    }

    @Test
    fun `capture failure is surfaced independently from the forwarding runtime`() = runTest(dispatcher) {
        val runtime = StatefulCaptureRuntime()
        val viewModel = FakeTrafficViewModelFactory.create(
            customProxyRuntime = runtime,
            customCaptureSessionControl = runtime,
        )
        advanceUntilIdle()

        runtime.failCapture("writer-unavailable")
        advanceUntilIdle()

        assertEquals("Capture unavailable (writer-unavailable)", viewModel.uiState.value.trafficErrorMessage)
    }

    private class StatefulCaptureRuntime : ProxyRuntimePort, CaptureSessionControlPort {
        private val runtimeState = MutableStateFlow<ProxyRuntimeState>(ProxyRuntimeState.Stopped)
        private val mutableCaptureState = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Inactive)
        override val state: StateFlow<ProxyRuntimeState> = runtimeState
        override val captureState: StateFlow<CaptureSessionState> = mutableCaptureState
        var startCalls: Int = 0
        var stopCalls: Int = 0
        var pauseCalls: Int = 0
        var resumeCalls: Int = 0

        override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult {
            startCalls += 1
            val binding = configuration.bindings.single()
            val handle = ProxyRuntimeHandle(
                runtimeId = "runtime-1",
                endpoints = ProxyEndpointSnapshot(
                    version = ProxyEndpointVersion(1L),
                    endpoints = listOf(
                        ProxyEndpoint(
                            host = binding.host,
                            port = binding.port,
                            scope = ProxyEndpointScope.LOOPBACK,
                            accessRequirement = ProxyAccessRequirement.LOCAL_PROCESS,
                        ),
                    ),
                ),
            )
            runtimeState.value = ProxyRuntimeState.Running(handle)
            mutableCaptureState.value = CaptureSessionState.Capturing(CaptureSessionId("session-1"))
            return ProxyStartResult.Running(handle)
        }

        override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
            stopCalls += 1
            runtimeState.value = ProxyRuntimeState.Stopped
            mutableCaptureState.value = CaptureSessionState.Inactive
            return ProxyStopResult.Stopped
        }

        override suspend fun pause(): CapturePauseResult {
            pauseCalls += 1
            if (runtimeState.value !is ProxyRuntimeState.Running) return CapturePauseResult.PROXY_INACTIVE
            mutableCaptureState.value = CaptureSessionState.Paused
            return CapturePauseResult.PAUSED
        }

        override suspend fun resume(): CaptureResumeResult {
            resumeCalls += 1
            val sessionId = CaptureSessionId("session-${resumeCalls + 1}")
            mutableCaptureState.value = CaptureSessionState.Capturing(sessionId)
            return CaptureResumeResult.Capturing(sessionId)
        }

        override suspend fun rotateForTrafficClear(): CaptureClearPreparation =
            CaptureClearPreparation.CANONICAL_SESSION_ROTATED

        fun failCapture(code: String) {
            mutableCaptureState.value = CaptureSessionState.Failed(code)
        }
    }
}
