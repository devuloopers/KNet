package com.devuloopers.knet.ui.desktop.traffic

import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.device.ProjectedStateFlow
import com.devuloopers.knet.application.contract.proxy.*
import com.devuloopers.knet.application.contract.traffic.*
import com.devuloopers.knet.connectivity.model.*
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.ui.desktop.traffic.model.CaptureState
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Toolbar commands freeze their device, while visible status follows the selected tab immediately. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficDeviceCaptureTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = TrafficSource.Device(RegisteredDeviceId("first"))
    private val second = TrafficSource.Device(RegisteredDeviceId("second"))
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun startAndPauseAreIndependentAndSwitchingTabsNeverChangesRecording() = runTest(dispatcher) {
        val runtime = ScopedRuntime()
        val viewModel = FakeTrafficViewModelFactory.create(customProxyRuntime = runtime, customCaptureSessionControl = runtime)
        try {
            advanceUntilIdle()
            assertTrue(runtime.commands.isEmpty(), "Opening Traffic must not pause any source")
            viewModel.selectSource(first)
            viewModel.processIntent(TrafficIntent.StartCapture)
            viewModel.selectSource(second)
            advanceUntilIdle()
            assertEquals(CaptureState.PAUSED, viewModel.uiState.value.captureState)
            assertEquals(listOf(first.captureWorkspace() to true), runtime.commands)
            viewModel.selectSource(first)
            assertEquals(CaptureState.CAPTURING, viewModel.uiState.value.captureState)
            viewModel.selectSource(second)
            assertEquals(CaptureState.PAUSED, viewModel.uiState.value.captureState)
            viewModel.processIntent(TrafficIntent.StartCapture)
            advanceUntilIdle()
            assertEquals(1, runtime.startCalls)
            viewModel.selectSource(first)
            viewModel.processIntent(TrafficIntent.PauseCapture)
            viewModel.selectSource(second)
            advanceUntilIdle()
            assertEquals(CaptureState.CAPTURING, viewModel.uiState.value.captureState)
            viewModel.selectSource(first)
            assertEquals(CaptureState.PAUSED, viewModel.uiState.value.captureState)
            viewModel.selectSource(TrafficSource.Other)
            assertEquals(CaptureState.PAUSED, viewModel.uiState.value.captureState)
            viewModel.processIntent(TrafficIntent.StartCapture)
            advanceUntilIdle()
            assertEquals(CaptureState.CAPTURING, viewModel.uiState.value.captureState)
            assertFalse(runtime.workspaceCaptureState.value.admission.enabledOn(first.captureWorkspace()))
            assertTrue(runtime.workspaceCaptureState.value.admission.enabledOn(second.captureWorkspace()))
            assertEquals(0, runtime.stopCalls)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun conflatedRapidActionsRetainTheLatestChoiceForEveryDevice() = runTest(dispatcher) {
        val runtime = ScopedRuntime()
        val viewModel = FakeTrafficViewModelFactory.create(customProxyRuntime = runtime, customCaptureSessionControl = runtime)
        try {
            advanceUntilIdle()
            viewModel.selectSource(first); viewModel.processIntent(TrafficIntent.StartCapture)
            viewModel.selectSource(second); viewModel.processIntent(TrafficIntent.StartCapture)
            viewModel.selectSource(first); viewModel.processIntent(TrafficIntent.PauseCapture)
            viewModel.selectSource(second)
            advanceUntilIdle()
            assertFalse(runtime.workspaceCaptureState.value.admission.enabledOn(first.captureWorkspace()))
            assertTrue(runtime.workspaceCaptureState.value.admission.enabledOn(second.captureWorkspace()))
            assertEquals(CaptureState.CAPTURING, viewModel.uiState.value.captureState)
            assertEquals(1, runtime.startCalls)
            assertEquals(0, runtime.stopCalls)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun delayedAcknowledgementAndFailureRetryDoNotRetargetTheCommandOrKillTheActor() = runTest(dispatcher) {
        val runtime = ScopedRuntime()
        val viewModel = FakeTrafficViewModelFactory.create(customProxyRuntime = runtime, customCaptureSessionControl = runtime)
        try {
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            runtime.changeGate = gate
            viewModel.selectSource(first); viewModel.processIntent(TrafficIntent.StartCapture)
            runCurrent()
            viewModel.selectSource(second); viewModel.processIntent(TrafficIntent.StartCapture)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(setOf(first.captureWorkspace(), second.captureWorkspace()), runtime.commands.map { it.first }.toSet())
            runtime.failNextChange = true
            viewModel.processIntent(TrafficIntent.PauseCapture)
            advanceUntilIdle()
            assertEquals("Capture could not be changed. Try again.", viewModel.uiState.value.trafficErrorMessage)
            viewModel.processIntent(TrafficIntent.PauseCapture)
            advanceUntilIdle()
            assertEquals(CaptureState.PAUSED, viewModel.uiState.value.captureState)
            viewModel.selectSource(first)
            assertEquals(CaptureState.CAPTURING, viewModel.uiState.value.captureState)
        } finally { viewModel.viewModelScope.cancel() }
    }

    private class ScopedRuntime : ProxyRuntime, CaptureSessionControl {
        override val state = MutableStateFlow<ProxyRuntimeState>(ProxyRuntimeState.Stopped)
        override val workspaceCaptureState = MutableStateFlow(WorkspaceCaptureSnapshot(admission = CaptureAdmissionPolicy(false)))
        override val captureState: StateFlow<CaptureSessionState> = ProjectedStateFlow(workspaceCaptureState) { it.sessionState }
        val commands = mutableListOf<Pair<PolicyWorkspace, Boolean>>()
        var startCalls = 0
        var stopCalls = 0
        var changeGate: CompletableDeferred<Unit>? = null
        var failNextChange = false
        override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult {
            startCalls++
            val binding = configuration.bindings.single()
            val handle = ProxyRuntimeHandle("runtime-$startCalls", ProxyEndpointSnapshot(ProxyEndpointVersion(startCalls.toLong()),
                listOf(ProxyEndpoint(binding.host, binding.port, ProxyEndpointScope.LOOPBACK, ProxyAccessRequirement.LOCAL_PROCESS))))
            state.value = ProxyRuntimeState.Running(handle)
            workspaceCaptureState.value = workspaceCaptureState.value.copy(sessionState = CaptureSessionState.Capturing(CaptureSessionId("writer-$startCalls")))
            return ProxyStartResult.Running(handle)
        }
        override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
            stopCalls++; state.value = ProxyRuntimeState.Stopped
            workspaceCaptureState.value = workspaceCaptureState.value.copy(sessionState = CaptureSessionState.Inactive)
            return ProxyStopResult.Stopped
        }
        private suspend fun change(workspace: PolicyWorkspace, enabled: Boolean) {
            changeGate?.await(); changeGate = null
            if (failNextChange) { failNextChange = false; error("Injected admission failure") }
            commands += workspace to enabled
            val current = workspaceCaptureState.value
            workspaceCaptureState.value = current.copy(admission = current.admission.copy(overrides = current.admission.overrides + (workspace to enabled)))
        }
        override suspend fun resume(workspace: PolicyWorkspace): CaptureResumeResult {
            change(workspace, true)
            return (captureState.value as? CaptureSessionState.Capturing)?.let { CaptureResumeResult.AlreadyCapturing(it.sessionId) }
                ?: CaptureResumeResult.ProxyInactive
        }
        override suspend fun pause(workspace: PolicyWorkspace): CapturePauseResult {
            change(workspace, false)
            return CapturePauseResult.PAUSED
        }
        override suspend fun pause(): CapturePauseResult = error("Toolbar must use source-specific pause")
        override suspend fun resume(): CaptureResumeResult = error("Toolbar must use source-specific resume")
        override suspend fun rotateForTrafficClear() = CaptureClearPreparation.CANONICAL_SESSION_ROTATED
    }
}
