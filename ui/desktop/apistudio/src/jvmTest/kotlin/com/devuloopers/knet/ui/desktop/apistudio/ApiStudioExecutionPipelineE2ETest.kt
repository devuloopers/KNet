package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.repository.ProxyEngineRepository
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        customMethod: String?,
        headers: Map<String, String>,
        body: String,
        bodyType: RequestBodyType,
        formParameters: Map<String, String>,
        auth: ApiRequestAuth,
        proxyPort: Int?
    ): ExecutionResult {
        lastExecutedUrl = url
        lastProxyPort = proxyPort

        return ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = emptyMap(),
            cookies = emptyMap(),
            responseBody = "Pipeline Success",
            latencyMs = 50,
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
    private val proxyStateFlow = MutableStateFlow<ProxyEngineState>(ProxyEngineState.Stopped)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createPipelineViewModel(executor: HttpExecutor): ApiStudioViewModel {
        val proxyRepo = object : ProxyEngineRepository {
            override fun engineState(): Flow<ProxyEngineState> = proxyStateFlow
            override suspend fun start(port: Int) {}
            override suspend fun stop() {}
        }

        val (getLayoutUseCase, saveLayoutUseCase) = createTestLayoutUseCases()
        val fakeInterceptionRepo = object : com.devuloopers.knet.domain.rules.repository.InterceptionSessionRepository {
            override val activeInterceptions = kotlinx.coroutines.flow.emptyFlow<List<com.devuloopers.knet.domain.rules.model.InterceptedTransaction>>()
            override suspend fun forwardRequest(transactionId: String, modifiedRequest: com.devuloopers.knet.domain.clientNetwork.model.HttpRequest) {}
            override suspend fun forwardResponse(transactionId: String, modifiedResponse: com.devuloopers.knet.domain.clientNetwork.model.HttpResponse) {}
            override suspend fun dropTransaction(transactionId: String) {}
            override suspend fun dropMatching(url: String, method: String) {}
            override suspend fun clearAll() {}
        }

        return ApiStudioViewModel(
            executeScriptedUseCase = com.devuloopers.knet.ui.desktop.apistudio.usecase.ExecuteScriptedApiRequestUseCase(
                executeUseCase = com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase(executor),
                formatResponseBodyUseCase = com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase(),
                ioDispatcher = testDispatcher
            ),
            observeProxyEngineStateUseCase = ObserveProxyEngineStateUseCase(proxyRepo),
            getWorkspaceLayoutUseCase = getLayoutUseCase,
            saveWorkspaceLayoutUseCase = saveLayoutUseCase,
            importRequestToStudioUseCase = com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase(),
            dropInterceptedTransactionUseCase = com.devuloopers.knet.domain.rules.usecase.DropInterceptedTransactionUseCase(fakeInterceptionRepo),
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `test pipeline execution when proxy is OFF (direct routing)`() = runTest {
        val spyExecutor = PipelineSpyHttpExecutor()
        val viewModel = createPipelineViewModel(spyExecutor)

        // 1. Ensure proxy is OFF
        proxyStateFlow.value = ProxyEngineState.Stopped

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
        proxyStateFlow.value = ProxyEngineState.Running(port = 8080)

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
}
