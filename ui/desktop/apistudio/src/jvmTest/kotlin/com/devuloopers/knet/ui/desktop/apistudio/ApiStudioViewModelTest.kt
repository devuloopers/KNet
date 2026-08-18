package com.devuloopers.knet.ui.desktop.apistudio

import com.devuloopers.knet.application.port.proxy.ProxyRuntimeConfiguration
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeHandle
import com.devuloopers.knet.application.port.proxy.ProxyRuntimePort
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.proxy.ProxyStartResult
import com.devuloopers.knet.application.port.proxy.ProxyStopReason
import com.devuloopers.knet.application.port.proxy.ProxyStopResult
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.usecase.breakpoint.DropMatchingBreakpointsUseCase
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.domain.rules.model.BreakpointRule
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
import kotlinx.coroutines.flow.StateFlow
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
        headers: Map<String, String>,
        body: OutboundRequestBody,
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
            timings = ExchangeTimings(totalMillis = 42L),
            responseSizeBytes = 20L,
            isSuccess = true
        )
    }

    override fun close() { }
}

/**
 * Test factory that creates an [ObserveProxyRuntimeStateUseCase] stub backed by a
 * [MutableStateFlow] emitting the given [initialState].
 * Defaults to [ProxyRuntimeState.Stopped] so tests run without proxy routing by default.
 */
fun createTestObserveProxyRuntimeStateUseCase(
    initialState: ProxyRuntimeState = ProxyRuntimeState.Stopped
): ObserveProxyRuntimeStateUseCase = ObserveProxyRuntimeStateUseCase(TestProxyRuntime(initialState))

class TestProxyRuntime(
    initialState: ProxyRuntimeState = ProxyRuntimeState.Stopped,
) : ProxyRuntimePort {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<ProxyRuntimeState> = mutableState

    fun publish(state: ProxyRuntimeState) {
        mutableState.value = state
    }

    override suspend fun start(configuration: ProxyRuntimeConfiguration): ProxyStartResult {
        val running = runningProxyRuntimeState(configuration.bindings.first().port)
        mutableState.value = running
        return ProxyStartResult.Running(running.handle)
    }

    override suspend fun stop(reason: ProxyStopReason): ProxyStopResult {
        mutableState.value = ProxyRuntimeState.Stopped
        return ProxyStopResult.Stopped
    }
}

fun runningProxyRuntimeState(port: Int): ProxyRuntimeState.Running = ProxyRuntimeState.Running(
    ProxyRuntimeHandle(
        runtimeId = "test-runtime-$port",
        endpoints = ProxyEndpointSnapshot(
            version = ProxyEndpointVersion(1L),
            endpoints = listOf(
                ProxyEndpoint(
                    host = "127.0.0.1",
                    port = port,
                    scope = ProxyEndpointScope.LOOPBACK,
                    accessRequirement = ProxyAccessRequirement.LOCAL_PROCESS,
                )
            ),
        ),
    )
)

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


class FakeTestBreakpointControl : BreakpointControlPort {
    var droppedUrl: String? = null
    var droppedMethod: String? = null

    override val pendingBreakpoints = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
    override val isEnabled = MutableStateFlow(true)
    override fun replaceRules(rules: List<BreakpointRule>) = Unit
    override fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
    }
    override fun setDecisionTimeoutMillis(timeoutMillis: Long) = Unit
    override suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean = true
    override suspend fun dropMatching(url: String, method: String): Int {
        droppedUrl = url
        droppedMethod = method
        return 1
    }
    override suspend fun clear(): Int = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeBreakpointControl = FakeTestBreakpointControl()

    private fun createTestViewModel(
        executeUseCase: ExecuteClientApiRequestUseCase,
        breakpointControl: BreakpointControlPort = fakeBreakpointControl
    ): ApiStudioViewModel {
        val (getLayoutUseCase, saveLayoutUseCase) = createTestLayoutUseCases()
        return ApiStudioViewModel(
            executeScriptedUseCase = ExecuteScriptedApiRequestUseCase(
                executeUseCase = executeUseCase,
                formatResponseBodyUseCase = FormatResponseBodyUseCase(),
                ioDispatcher = testDispatcher
            ),
            observeProxyRuntimeStateUseCase = createTestObserveProxyRuntimeStateUseCase(),
            getWorkspaceLayoutUseCase = getLayoutUseCase,
            saveWorkspaceLayoutUseCase = saveLayoutUseCase,
            importRequestToStudioUseCase = com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase(),
            dropMatchingBreakpointsUseCase = DropMatchingBreakpointsUseCase(breakpointControl),
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
        assertNull(state.responseInspection)
    }

    @Test
    fun `executeRequest maps queryParams headers cookies auth and updates response inspection`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://api.example.com/v1/users")
        viewModel.updateMethod("POST")
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExecutionState.SUCCESS, state.executionState)
        val response = assertNotNull(state.responseInspection)
        assertEquals(200, response.statusCode)
        assertEquals("OK", response.statusText)
        assertEquals("application/json", response.headers["content-type"])
        assertTrue(response.responseBody.contains("\"status\": \"success\""))
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
        viewModel.updateBodyMode(com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode.JSON)
        viewModel.updateAuthState(com.devuloopers.knet.ui.desktop.httppanel.model.AuthState(
            authType = com.devuloopers.knet.ui.desktop.httppanel.model.AuthType.BEARER_TOKEN,
            bearerToken = "secret_token_abc"
        ))
        viewModel.updateCookies(listOf("session_id" to "sess_999"))
        viewModel.updateScripts("// pre-request", "// test assertion")
        viewModel.updateActiveSubTab(com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab.HEADERS)

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
        assertEquals(com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab.HEADERS, state.activeSubTab)
    }

    @Test
    fun `clearResponse resets response inspection to null and executionState to IDLE`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://api.example.com/v1/users")
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.responseInspection)
        assertEquals(ExecutionState.SUCCESS, viewModel.uiState.value.executionState)

        viewModel.clearResponse()

        assertNull(viewModel.uiState.value.responseInspection)
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
        assertNotNull(viewModel.uiState.value.responseInspection)
    }


    @Test
    fun `executeRequest with invalid host populates executionState ERROR and HostNotFound failure reason`() = runTest {
        val failingExecutor = object : HttpExecutor {
            override suspend fun execute(
                url: String,
                method: HttpMethod,
                headers: Map<String, String>,
                body: OutboundRequestBody,
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
        val response = assertNotNull(state.responseInspection)
        assertEquals(0, response.statusCode)
        assertTrue(response.failureReason is com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason.HostNotFound)
        assertEquals("api.example.com", response.failureReason.host)
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
        assertTrue(state.editorState.bodyPayload.contains("\"item\": \"laptop\""))
        assertEquals(listOf("session" to "xyz123"), state.editorState.cookies)
    }

    @Test
    fun clearSessionContext_resetsEditorStateResponseInspectionAndSessionContextToNone() = runTest(testDispatcher) {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://stg-04astra.cnbc.com/graphql")
        viewModel.updateMethod("POST")
        viewModel.updateBodyPayload("{\"query\": \"...\"}")
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.responseInspection)
        assertEquals("https://stg-04astra.cnbc.com/graphql", viewModel.uiState.value.editorState.url)

        viewModel.clearSessionContext()

        val state = viewModel.uiState.value
        assertEquals("", state.editorState.url)
        assertEquals("GET", state.editorState.method)
        assertEquals("", state.editorState.bodyPayload)
        assertNull(state.responseInspection)
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

    @Test
    fun `updateBodyMode directly sets strongly typed RequestBodyMode`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateBodyMode(com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode.GRAPHQL)

        val state = viewModel.uiState.value.editorState
        assertEquals(com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode.GRAPHQL, state.bodyState.mode)
        assertEquals("GRAPHQL", state.bodyType)
    }

    @Test
    fun `importRequestSpec auto detects and hydrates GraphQL payload via RequestBodyState from`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        val gqlJson = """{"query": "query GetSymbols { symbols { id } }", "operationName": "GetSymbols"}"""
        val spec = com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
            method = HttpMethod.POST,
            url = "https://api.example.com/graphql",
            headers = listOf("content-type" to "application/json"),
            bodyPayload = gqlJson
        )

        viewModel.importRequestSpec(spec)

        val state = viewModel.uiState.value.editorState
        assertEquals(com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode.GRAPHQL, state.bodyState.mode)
        assertTrue(state.bodyState.graphQlState.queryText.contains("GetSymbols"))
        assertEquals("GetSymbols", state.bodyState.graphQlState.operationName)
    }

    @Test
    fun `updateScriptLanguage sets strongly typed ScriptLanguage enum`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateScriptLanguage(com.devuloopers.knet.scripting.model.ScriptLanguage.KOTLIN)

        val state = viewModel.uiState.value.editorState
        assertEquals(com.devuloopers.knet.scripting.model.ScriptLanguage.KOTLIN, state.scriptLanguage)
    }

    @Test
    fun `cancelExecution resets executionState to IDLE and invokes dropInterceptedTransactionUseCase`() = runTest {
        val testExecutor = TestHttpExecutor()
        val customInterceptionRepo = FakeTestBreakpointControl()
        val viewModel = createTestViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(testExecutor),
            breakpointControl = customInterceptionRepo
        )

        viewModel.updateUrl("https://api.example.com/cancel-test")
        viewModel.updateMethod("POST")

        // Trigger cancel
        viewModel.cancelExecution()
        testScheduler.advanceUntilIdle()

        assertEquals(ExecutionState.IDLE, viewModel.uiState.value.executionState)
        assertEquals("https://api.example.com/cancel-test", customInterceptionRepo.droppedUrl)
        assertEquals("POST", customInterceptionRepo.droppedMethod)
    }

    @Test
    fun `executeRequest failure triggers dropInterceptedTransactionUseCase for interception cleanup`() = runTest {
        val failingExecutor = object : HttpExecutor {
            override suspend fun execute(
                url: String,
                method: HttpMethod,
                headers: Map<String, String>,
                body: OutboundRequestBody,
                auth: ApiRequestAuth,
                proxyPort: Int?
            ): ExecutionResult {
                return ExecutionResult(
                    statusCode = 0,
                    statusText = "Timeout",
                    headers = emptyMap(),
                    responseBody = "",
                    timings = ExchangeTimings(totalMillis = 10000L),
                    responseSizeBytes = 0L,
                    isSuccess = false,
                    errorMessage = "Request timeout has expired"
                )
            }

            override fun close() {}
        }

        val customInterceptionRepo = FakeTestBreakpointControl()
        val viewModel = createTestViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(failingExecutor),
            breakpointControl = customInterceptionRepo
        )

        viewModel.updateUrl("https://api.example.com/timeout-test")
        viewModel.updateMethod("GET")

        viewModel.executeRequest()
        testScheduler.advanceUntilIdle()

        assertEquals(ExecutionState.ERROR, viewModel.uiState.value.executionState)
        assertEquals("https://api.example.com/timeout-test", customInterceptionRepo.droppedUrl)
        assertEquals("GET", customInterceptionRepo.droppedMethod)
    }
}
