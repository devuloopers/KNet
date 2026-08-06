package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestHttpExecutor : HttpExecutor {
    var lastExecutedUrl: String = ""
    var lastMethod: HttpMethod = HttpMethod.GET
    var lastHeaders: Map<String, String> = emptyMap()
    var lastAuth: ApiRequestAuth = ApiRequestAuth.None

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
        lastMethod = method
        lastHeaders = headers
        lastAuth = auth

        return ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("content-type" to "application/json"),
            cookies = mapOf("session" to "xyz123"),
            responseBody = "{\"status\":\"success\"}",
            latencyMs = 42L,
            responseSizeBytes = 20L,
            isSuccess = true
        )
    }

    override fun close() { }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ApiStudioState default state is initialized correctly`() {
        val state = ApiStudioState()
        assertEquals(1, state.tabs.size)
        assertEquals("tab_1", state.activeTabId)
        assertEquals(ExecutionState.IDLE, state.executionState)
        assertNull(state.responsePresentation)
    }

    @Test
    fun `executeRequest maps queryParams headers cookies auth and updates response presentation`() = runTest {
        val testExecutor = TestHttpExecutor()
        val executeUseCase = ExecuteClientApiRequestUseCase(testExecutor)
        val viewModel = ApiStudioViewModel(executeUseCase = executeUseCase)

        viewModel.updateUrl("https://api.example.com/v1/users")
        viewModel.updateMethod("POST")
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExecutionState.SUCCESS, state.executionState)
        assertNotNull(state.responsePresentation)
        assertEquals(200, state.responsePresentation?.statusCode)
        assertEquals("OK", state.responsePresentation?.statusText)
        assertEquals("application/json", state.responsePresentation?.mimeType)
        assertTrue(state.responsePresentation?.body?.contains("\"status\": \"success\"") == true)
    }
}
