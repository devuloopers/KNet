package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.domain.request.descriptor.HttpRequestDescriptorStrategy
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.breakpoint.DropMatchingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.domain.collection.usecase.GetSavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveRequestToCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.domain.payload.PayloadStrategyRegistry
import com.devuloopers.knet.ui.desktop.apistudio.usecase.AutoSaveApiSessionUseCase
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A spy HTTP executor that captures the parameters passed to it, allowing
 * us to verify whether the proxy port was correctly passed down from the ViewModel.
 */
class PipelineSpyHttpExecutor : HttpExecutor {
    var lastExecutedUrl: String = ""
    var lastProxyPort: Int? = -1 // Initialize with -1 to differentiate from null

    override suspend fun execute(
        url: String,
        method: HttpMethod,
        headers: Map<String, String>,
        body: OutboundRequestBody,
        auth: ApiRequestAuth,
        proxyPort: Int?,
        httpVersionPreference: HttpVersionPreference,
    ): ExecutionResult {
        lastExecutedUrl = url
        lastProxyPort = proxyPort

        return ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = emptyMap(),
            cookies = emptyMap(),
            responseBody = "Pipeline Success",
            timings = ExchangeTimings(totalMillis = 50),
            responseSizeBytes = 16,
            isSuccess = true,
            errorMessage = null,
            failureReason = null
        )
    }

    override fun close() {
        // no-op
    }
}

/**
 * End-to-end integration test verifying the single-responsibility execution pipeline
 * from ApiStudioViewModel -> ExecuteClientApiRequestUseCase -> HttpExecutor.
 * 
 * Verifies that the ViewModel correctly acts as a pure executor and accurately 
 * propagates the live proxy port state to route traffic dynamically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioExecutionPipelineE2ETest {

    private val testDispatcher = StandardTestDispatcher()
    private val proxyRuntime = TestProxyRuntime()
    private val captureControl = TestCaptureSessionControl()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createPipelineViewModel(executor: HttpExecutor): ApiStudioViewModel {
        val (getLayoutUseCase, updateLayoutUseCase) = createTestLayoutUseCases()
        val (observeApplicationSettings, updateApplicationSettings) =
            createTestApplicationSettingsUseCases()
        val breakpointControl = FakeTestBreakpointControl()
        val collectionsRepository = TestCollectionsRepository()

        return ApiStudioViewModel(
            executeApiStudioRequestUseCase = com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioRequestUseCase(
                executeRequest = com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase(executor),
                formatResponseBody = com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase(),
                scriptExecution = com.devuloopers.knet.application.contract.script.UnavailableScriptExecutor,
                ioDispatcher = testDispatcher
            ),
            observeProxyRuntimeStateUseCase = ObserveProxyRuntimeStateUseCase(proxyRuntime),
            observeTrafficCaptureStateUseCase = ObserveTrafficCaptureStateUseCase(captureControl),
            getWorkspaceLayoutUseCase = getLayoutUseCase,
            updateWorkspaceLayoutUseCase = updateLayoutUseCase,
            observeApplicationSettingsUseCase = observeApplicationSettings,
            updateApplicationSettingsUseCase = updateApplicationSettings,
            importRequestToStudioUseCase = com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase(),
            describeRequestUseCase = DescribeRequestUseCase(listOf(HttpRequestDescriptorStrategy())),
            dropMatchingBreakpointsUseCase = DropMatchingBreakpointsUseCase(breakpointControl),
            syncBodyStateUseCase = SyncBodyStateUseCase(
                PayloadStrategyRegistry(listOf(GraphQlPayloadMapper()))
            ),
            autoSaveApiSessionUseCase = AutoSaveApiSessionUseCase(
                SaveUnsavedRequestUseCase(collectionsRepository),
                UpdateRequestInCollectionUseCase(collectionsRepository)
            ),
            getSavedRequestUseCase = GetSavedRequestUseCase(collectionsRepository),
            saveRequestToCollectionUseCase = SaveRequestToCollectionUseCase(collectionsRepository),
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `test pipeline execution when proxy is OFF (direct routing)`() = runTest {
        val spyExecutor = PipelineSpyHttpExecutor()
        val viewModel = createPipelineViewModel(spyExecutor)

        // 1. Ensure proxy is OFF
        proxyRuntime.publish(ProxyRuntimeState.Stopped)

        // 2. Configure request
        viewModel.updateUrl("https://api.example.com/test")
        
        // 3. Execute
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Verify pure execution outcome
        assertEquals(ExecutionState.SUCCESS, viewModel.uiState.value.executionState)
        
        // 5. Verify the pipeline correctly passed proxyPort = null (direct routing)
        assertEquals("https://api.example.com/test", spyExecutor.lastExecutedUrl)
        assertEquals(null, spyExecutor.lastProxyPort)
    }

    @Test
    fun `test pipeline execution when proxy is ON (proxy routing)`() = runTest {
        val spyExecutor = PipelineSpyHttpExecutor()
        val viewModel = createPipelineViewModel(spyExecutor)

        // 1. Ensure proxy is ON and running on port 8080
        proxyRuntime.publish(runningProxyRuntimeState(port = 8080))
        captureControl.publish(CaptureSessionState.Capturing(CaptureSessionId("capturing")))

        // 2. Configure request
        viewModel.updateUrl("https://api.example.com/secure")
        
        // 3. Execute
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Verify pure execution outcome
        assertEquals(ExecutionState.SUCCESS, viewModel.uiState.value.executionState)
        
        // 5. Verify the pipeline correctly intercepted the proxy port and passed it down
        assertEquals("https://api.example.com/secure", spyExecutor.lastExecutedUrl)
        assertEquals(8080, spyExecutor.lastProxyPort)
    }

    @Test
    fun `pausing capture switches a running proxy route back to direct execution`() = runTest {
        val spyExecutor = PipelineSpyHttpExecutor()
        val viewModel = createPipelineViewModel(spyExecutor)

        proxyRuntime.publish(runningProxyRuntimeState(port = 8080))
        captureControl.publish(CaptureSessionState.Capturing(CaptureSessionId("capturing-before-pause")))
        viewModel.updateUrl("https://api.example.com/through-proxy")

        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ExecutionState.SUCCESS, viewModel.uiState.value.executionState)
        assertEquals(8080, spyExecutor.lastProxyPort)

        captureControl.publish(CaptureSessionState.Paused)
        viewModel.updateUrl("https://api.example.com/direct-while-paused")

        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ExecutionState.SUCCESS, viewModel.uiState.value.executionState)
        assertEquals(null, spyExecutor.lastProxyPort)
    }
}
