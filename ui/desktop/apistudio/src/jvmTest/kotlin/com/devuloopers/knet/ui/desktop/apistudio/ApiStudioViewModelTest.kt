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
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.apistudio.usecase.ExecuteScriptedApiRequestUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
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

/**
 * Test factory that creates an [ObserveProxyEngineStateUseCase] stub backed by a
 * [MutableStateFlow] emitting the given [initialState].
 * Defaults to [ProxyEngineState.Stopped] so tests run without proxy routing by default.
 */
fun createTestObserveProxyEngineStateUseCase(
    initialState: ProxyEngineState = ProxyEngineState.Stopped
): ObserveProxyEngineStateUseCase {
    val stateFlow = MutableStateFlow(initialState)
    return ObserveProxyEngineStateUseCase(
        repository = object : ProxyEngineRepository {
            override fun engineState(): Flow<ProxyEngineState> = stateFlow
            override suspend fun start(port: Int) { stateFlow.value = ProxyEngineState.Running(port) }
            override suspend fun stop() { stateFlow.value = ProxyEngineState.Stopped }
        }
    )
}

fun createTestLayoutUseCases(
    initialSettings: WorkspaceLayoutSettings = WorkspaceLayoutSettings()
): Pair<GetWorkspaceLayoutUseCase, SaveWorkspaceLayoutUseCase> {
    val stateFlow = MutableStateFlow(initialSettings)
    val repo = object : WidgetPreferencesRepository {
        override val settingsFlow: Flow<WorkspaceLayoutSettings> = stateFlow
        override suspend fun saveSettings(settings: WorkspaceLayoutSettings) {
            stateFlow.value = settings
        }
    }
    return GetWorkspaceLayoutUseCase(repo) to SaveWorkspaceLayoutUseCase(repo)
}


@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createTestViewModel(
        executeUseCase: ExecuteClientApiRequestUseCase
    ): ApiStudioViewModel {
        val (getLayoutUseCase, saveLayoutUseCase) = createTestLayoutUseCases()
        return ApiStudioViewModel(
            executeScriptedUseCase = ExecuteScriptedApiRequestUseCase(
                executeUseCase = executeUseCase,
                formatResponseBodyUseCase = FormatResponseBodyUseCase(),
                ioDispatcher = testDispatcher
            ),
            observeProxyEngineStateUseCase = createTestObserveProxyEngineStateUseCase(),
            getWorkspaceLayoutUseCase = getLayoutUseCase,
            saveWorkspaceLayoutUseCase = saveLayoutUseCase,
            importRequestToStudioUseCase = com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase(),
            ioDispatcher = testDispatcher
        )
    }

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
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

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
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

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
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

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
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

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
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.editor.RequestSubTab.HEADERS, state.activeSubTab)
    }

    @Test
    fun `clearResponse resets responsePresentation to null and executionState to IDLE`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://api.example.com/v1/users")
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
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://api.example.com/v1/users")
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


    @Test
    fun `executeRequest with invalid host populates executionState ERROR and HostNotFound failure reason`() = runTest {
        val failingExecutor = object : HttpExecutor {
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
                val reason = com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason.HostNotFound(
                    host = "api.example.com",
                    detail = "api.example.com: No such host is known"
                )
                return ExecutionResult(
                    statusCode = 0,
                    statusText = "Execution Error",
                    isSuccess = false,
                    errorMessage = "api.example.com: No such host is known",
                    failureReason = reason
                )
            }
            override fun close() { }
        }

        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(failingExecutor))

        viewModel.updateUrl("https://api.example.com/v1/users")
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExecutionState.ERROR, state.executionState)
        assertNotNull(state.responsePresentation)
        assertEquals(0, state.responsePresentation?.statusCode)
        assertTrue(state.responsePresentation?.failureReason is com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason.HostNotFound)
        assertEquals("api.example.com", (state.responsePresentation?.failureReason as com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason.HostNotFound).host)
    }

    @Test
    fun importRequestSpec_createsNewTabAndPopulatesEditorState() = runTest(testDispatcher) {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        val spec = com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
            method = HttpMethod.POST,
            url = "https://api.example.com/v1/orders",
            headers = listOf("Authorization" to "Bearer secret_token"),
            queryParams = listOf("filter" to "active"),
            bodyPayload = "{\"item\": \"laptop\"}",
            cookies = listOf("session" to "xyz123")
        )

        val initialTabCount = viewModel.uiState.value.tabs.size
        viewModel.importRequestSpec(spec, title = "Create Order")

        val state = viewModel.uiState.value
        assertEquals(initialTabCount + 1, state.tabs.size)
        assertEquals("POST", state.editorState.method)
        assertEquals("https://api.example.com/v1/orders", state.editorState.url)
        assertEquals(listOf("Authorization" to "Bearer secret_token"), state.editorState.headers)
        assertEquals(listOf("filter" to "active"), state.editorState.queryParams)
        assertEquals("{\"item\": \"laptop\"}", state.editorState.bodyPayload)
        assertEquals(listOf("session" to "xyz123"), state.editorState.cookies)
    }

    @Test
    fun clearSessionContext_resetsEditorStateResponsePresentationAndSessionContextToNone() = runTest(testDispatcher) {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://stg-04astra.cnbc.com/graphql")
        viewModel.updateMethod("POST")
        viewModel.updateBodyPayload("{\"query\": \"...\"}")
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.responsePresentation)
        assertEquals("https://stg-04astra.cnbc.com/graphql", viewModel.uiState.value.editorState.url)

        viewModel.clearSessionContext()

        val state = viewModel.uiState.value
        assertEquals("", state.editorState.url)
        assertEquals("GET", state.editorState.method)
        assertEquals("", state.editorState.bodyPayload)
        assertNull(state.responsePresentation)
        assertEquals(ExecutionState.IDLE, state.executionState)
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.model.SessionType.NONE, state.editorState.sessionType)
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None, state.sessionContext)
    }

    @Test
    fun closeTab_resetsStateWhenLastTabIsClosed() = runTest(testDispatcher) {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        val spec = com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
            method = HttpMethod.POST,
            url = "https://api.example.com/v1/users",
            bodyPayload = "{\"user\":\"admin\"}"
        )
        val tabId = viewModel.importRequestSpec(spec)

        assertEquals("https://api.example.com/v1/users", viewModel.uiState.value.editorState.url)

        // Close all tabs
        val allTabIds = viewModel.uiState.value.tabs.map { it.id }
        allTabIds.forEach { viewModel.closeTab(it) }

        val state = viewModel.uiState.value
        assertTrue(state.tabs.isEmpty())
        assertEquals("", state.editorState.url)
        assertEquals("", state.editorState.bodyPayload)
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.model.SessionType.NONE, state.editorState.sessionType)
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None, state.sessionContext)
    }

    @Test
    fun closeTab_resetsActiveStateToBlankOnActiveSessionDeletionWithoutAutoSelectingAdjacentTabs() = runTest(testDispatcher) {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        val spec1 = com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
            method = HttpMethod.GET,
            url = "https://api.example.com/v1/users"
        )
        val spec2 = com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
            method = HttpMethod.POST,
            url = "https://api.example.com/v1/orders"
        )
        val tabId1 = viewModel.importRequestSpec(spec1)
        val tabId2 = viewModel.importRequestSpec(spec2)

        assertEquals("https://api.example.com/v1/orders", viewModel.uiState.value.editorState.url)

        // Close active tab2
        viewModel.closeTab(tabId2)

        val state = viewModel.uiState.value
        assertEquals(2, state.tabs.size)
        assertEquals("", state.editorState.url)
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.model.SessionType.NONE, state.editorState.sessionType)
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None, state.sessionContext)
    }
}
