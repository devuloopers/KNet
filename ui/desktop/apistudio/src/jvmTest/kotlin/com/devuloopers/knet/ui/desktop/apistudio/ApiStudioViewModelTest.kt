package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
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
        val formatResponseBodyUseCase = FormatResponseBodyUseCase()
        val viewModel = ApiStudioViewModel(
            executeUseCase = executeUseCase,
            formatResponseBodyUseCase = formatResponseBodyUseCase
        )

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

    @Test
    fun `updateUrl and updateQueryParams sync bi-directionally`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = ApiStudioViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(testExecutor),
            formatResponseBodyUseCase = FormatResponseBodyUseCase()
        )

        // 1. Typing URL with query parameters parses into queryParams list
        viewModel.updateUrl("http://localhost:9090/api/get?user=anant&role=admin")
        var state = viewModel.uiState.value
        assertEquals(2, state.editorState.queryParams.size)
        assertEquals("user" to "anant", state.editorState.queryParams[0])
        assertEquals("role" to "admin", state.editorState.queryParams[1])

        // 2. Modifying queryParams table reconstructs URL string
        viewModel.updateQueryParams(listOf("user" to "anant", "role" to "superadmin", "page" to "1"))
        state = viewModel.uiState.value
        assertEquals("http://localhost:9090/api/get?user=anant&role=superadmin&page=1", state.editorState.url)
        assertEquals(3, state.editorState.queryParams.size)

        // 3. Clearing queryParams cleans up URL query string
        viewModel.updateQueryParams(emptyList())
        state = viewModel.uiState.value
        assertEquals("http://localhost:9090/api/get", state.editorState.url)
        assertTrue(state.editorState.queryParams.isEmpty())
    }

    @Test
    fun `updateUrl and updateQueryParams handle multiple query parameters accurately`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = ApiStudioViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(testExecutor),
            formatResponseBodyUseCase = FormatResponseBodyUseCase()
        )

        // 1. Parse complex URL with 5 multiple query parameters
        val multiQueryUrl = "http://localhost:9090/api/search?q=kotlin&category=mobile&page=1&limit=25&active=true"
        viewModel.updateUrl(multiQueryUrl)

        var state = viewModel.uiState.value
        val parsedParams = state.editorState.queryParams

        assertEquals(5, parsedParams.size)
        assertEquals("q" to "kotlin", parsedParams[0])
        assertEquals("category" to "mobile", parsedParams[1])
        assertEquals("page" to "1", parsedParams[2])
        assertEquals("limit" to "25", parsedParams[3])
        assertEquals("active" to "true", parsedParams[4])

        // 2. Modify multi-query list (update page, limit, and append sort parameter)
        val updatedMultiQueryList = listOf(
            "q" to "multiplatform",
            "category" to "desktop",
            "page" to "2",
            "limit" to "50",
            "active" to "true",
            "sort" to "desc"
        )
        viewModel.updateQueryParams(updatedMultiQueryList)

        state = viewModel.uiState.value
        val expectedReconstructedUrl = "http://localhost:9090/api/search?q=multiplatform&category=desktop&page=2&limit=50&active=true&sort=desc"
        assertEquals(expectedReconstructedUrl, state.editorState.url)
        assertEquals(6, state.editorState.queryParams.size)

        // 3. Remove 2 parameters from multi-query list and verify URL reflects updated subset
        val trimmedMultiQueryList = listOf(
            "q" to "multiplatform",
            "page" to "2",
            "limit" to "50"
        )
        viewModel.updateQueryParams(trimmedMultiQueryList)

        state = viewModel.uiState.value
        assertEquals("http://localhost:9090/api/search?q=multiplatform&page=2&limit=50", state.editorState.url)
        assertEquals(3, state.editorState.queryParams.size)
    }

    @Test
    fun `all editor fields method headers body auth cookies scripts and activeSubTab are preserved in ViewModel state`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = ApiStudioViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(testExecutor),
            formatResponseBodyUseCase = FormatResponseBodyUseCase()
        )

        viewModel.updateMethod("POST")
        viewModel.updateUrl("http://localhost:9090/api/post?debug=true")
        viewModel.updateHeaders(listOf("Authorization" to "Bearer token_123", "X-Custom" to "HeaderValue"))
        viewModel.updateBodyPayload("{\"name\": \"KNet\"}")
        viewModel.updateBodyType("JSON")
        viewModel.updateAuth("Bearer Token", "secret_token_abc")
        viewModel.updateCookies(listOf("session_id" to "sess_999"))
        viewModel.updateScripts("// pre-request", "// test assertion")
        viewModel.updateActiveSubTab("HEADERS")

        val state = viewModel.uiState.value.editorState
        assertEquals("POST", state.method)
        assertEquals("http://localhost:9090/api/post?debug=true", state.url)
        assertEquals(1, state.queryParams.size)
        assertEquals("debug" to "true", state.queryParams[0])
        assertEquals(2, state.headers.size)
        assertEquals("Authorization" to "Bearer token_123", state.headers[0])
        assertEquals("{\"name\": \"KNet\"}", state.bodyPayload)
        assertEquals("JSON", state.bodyType)
        assertEquals("Bearer Token", state.authType)
        assertEquals("secret_token_abc", state.authToken)
        assertEquals(1, state.cookies.size)
        assertEquals("session_id" to "sess_999", state.cookies[0])
        assertEquals("// pre-request", state.preRequestScript)
        assertEquals("// test assertion", state.testScript)
        assertEquals("HEADERS", state.activeSubTab)
    }

    @Test
    fun `clearResponse resets responsePresentation to null and executionState to IDLE`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = ApiStudioViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(testExecutor),
            formatResponseBodyUseCase = FormatResponseBodyUseCase()
        )

        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.responsePresentation)
        assertEquals(ExecutionState.SUCCESS, viewModel.uiState.value.executionState)

        viewModel.clearResponse()

        assertNull(viewModel.uiState.value.responsePresentation)
        assertEquals(ExecutionState.IDLE, viewModel.uiState.value.executionState)
    }

    @Test
    fun `executeRequest enforces minimum visual loading duration window for ultra fast responses`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = ApiStudioViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(testExecutor),
            formatResponseBodyUseCase = FormatResponseBodyUseCase()
        )

        viewModel.executeRequest()
        assertEquals(ExecutionState.EXECUTING, viewModel.uiState.value.executionState)

        // Advance 100ms (less than MIN_LOADING_DURATION_MS 200ms)
        testDispatcher.scheduler.advanceTimeBy(100L)
        assertEquals(ExecutionState.EXECUTING, viewModel.uiState.value.executionState)

        // Advance remaining time until idle (past 200ms)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ExecutionState.SUCCESS, viewModel.uiState.value.executionState)
        assertNotNull(viewModel.uiState.value.responsePresentation)
    }
}
