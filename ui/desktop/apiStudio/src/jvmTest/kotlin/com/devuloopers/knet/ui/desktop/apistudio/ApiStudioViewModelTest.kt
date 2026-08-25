package com.devuloopers.knet.ui.desktop.apistudio

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
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.application.usecase.breakpoint.DropMatchingBreakpointsUseCase
import com.devuloopers.knet.application.port.script.UnavailableScriptExecutionPort
import com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioRequestUseCase
import com.devuloopers.knet.connectivity.model.ProxyAccessRequirement
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointScope
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.request.descriptor.HttpRequestDescriptorStrategy
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.request.usecase.DescribeRequestUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.collection.usecase.GetSavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveRequestToCollectionUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveUnsavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.UpdateRequestInCollectionUseCase
import com.devuloopers.knet.domain.payload.PayloadStrategyRegistry
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.StandardApplicationProtocol
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.domain.settings.usecase.UpdateApplicationSettingsUseCase
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.UpdateWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.apistudio.usecase.AutoSaveApiSessionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContextSerializer
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CollectionSaveMode
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entries(vararg values: Pair<String, String>): List<KeyValueEntry> =
    values.mapIndexed { index, (key, value) -> KeyValueEntry("test-entry-$index", key, value) }

private fun KeyValueEntry.asPair(): Pair<String, String> = key to value

class TestHttpExecutor : HttpExecutor {
    var lastExecutedUrl: String = ""
    var lastMethod: HttpMethod = HttpMethod.GET
    var lastHeaders: Map<String, String> = emptyMap()
    var lastAuth: ApiRequestAuth = ApiRequestAuth.None
    var lastHttpVersionPreference: HttpVersionPreference = HttpVersionPreference.AUTO

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
        lastMethod = method
        lastHeaders = headers
        lastAuth = auth
        lastHttpVersionPreference = httpVersionPreference

        return ExecutionResult(
            statusCode = 200,
            statusText = "OK",
            headers = mapOf("content-type" to "application/json"),
            cookies = mapOf("session" to "xyz123"),
            responseBody = "{\"status\":\"success\"}",
            timings = ExchangeTimings(totalMillis = 42L),
            responseSizeBytes = 20L,
            isSuccess = true,
            protocol = ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_0),
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

fun createTestObserveTrafficCaptureStateUseCase(
    initialState: CaptureSessionState = CaptureSessionState.Inactive,
): ObserveTrafficCaptureStateUseCase = ObserveTrafficCaptureStateUseCase(TestCaptureSessionControl(initialState))

class TestCaptureSessionControl(
    initialState: CaptureSessionState = CaptureSessionState.Inactive,
) : CaptureSessionControlPort {
    private val mutableState = MutableStateFlow(initialState)
    override val captureState: StateFlow<CaptureSessionState> = mutableState

    fun publish(state: CaptureSessionState) {
        mutableState.value = state
    }

    override suspend fun pause(): CapturePauseResult {
        mutableState.value = CaptureSessionState.Paused
        return CapturePauseResult.PAUSED
    }

    override suspend fun resume(): CaptureResumeResult {
        val sessionId = CaptureSessionId("test-capture-session")
        mutableState.value = CaptureSessionState.Capturing(sessionId)
        return CaptureResumeResult.Capturing(sessionId)
    }

    override suspend fun rotateForTrafficClear(): CaptureClearPreparation =
        CaptureClearPreparation.CANONICAL_SESSION_INACTIVE
}

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
): Pair<GetWorkspaceLayoutUseCase, UpdateWorkspaceLayoutUseCase> {
    val stateFlow = MutableStateFlow(initialSettings)
    val repo = object : WidgetPreferencesRepository {
        override val settingsFlow: Flow<WorkspaceLayoutSettings> = stateFlow
        override suspend fun updateSettings(
            transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings,
        ) {
            stateFlow.value = transform(stateFlow.value)
        }
    }
    return GetWorkspaceLayoutUseCase(repo) to UpdateWorkspaceLayoutUseCase(repo)
}

fun createTestApplicationSettingsUseCases(): Pair<ObserveApplicationSettingsUseCase, UpdateApplicationSettingsUseCase> {
    val stateFlow = MutableStateFlow(ApplicationSettings())
    val repository = object : ApplicationSettingsRepository {
        override val settings: Flow<ApplicationSettings> = stateFlow
        override suspend fun update(transform: (ApplicationSettings) -> ApplicationSettings) {
            stateFlow.value = transform(stateFlow.value)
        }
    }
    return ObserveApplicationSettingsUseCase(repository) to UpdateApplicationSettingsUseCase(repository)
}


class FakeTestBreakpointControl : BreakpointControlPort {
    var droppedUrl: String? = null
    var droppedMethod: String? = null

    override val pendingBreakpoints = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
    override val isEnabled = MutableStateFlow(true)
    override fun replaceRules(rules: List<BreakpointRule>) = Unit
    override suspend fun setEnabled(enabled: Boolean) {
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

/** In-memory collection boundary used by API Studio ViewModel tests. */
class TestCollectionsRepository : CollectionsRepository {
    val drafts = linkedMapOf<String, SavedApiRequest>()
    val saved = linkedMapOf<String, SavedApiRequest>()
    var promotionFailure: Exception? = null

    override fun observeCollections(): Flow<List<ApiCollection>> = flowOf(emptyList())
    override suspend fun getCollectionById(id: String): ApiCollection? = null
    override suspend fun getRequestById(id: String): SavedApiRequest? = drafts[id] ?: saved[id]
    override suspend fun saveCollection(collection: ApiCollection) = Unit
    override suspend fun deleteCollection(collectionId: String) = Unit
    override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) = Unit
    override suspend fun deleteFolder(folderId: String) = Unit
    override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
        saved[request.id] = request
    }
    override suspend fun deleteRequest(requestId: String) {
        saved.remove(requestId)
    }
    override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> = flowOf(emptyList())
    override suspend fun saveUnsavedRequest(request: SavedApiRequest) {
        drafts[request.id] = request
    }
    override suspend fun deleteUnsavedRequest(requestId: String) {
        drafts.remove(requestId)
    }
    override suspend fun saveUnsavedToNewCollectionTx(
        collection: ApiCollection,
        folder: CollectionFolder,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    ) {
        promotionFailure?.let { throw it }
        saved[request.id] = request
        drafts.remove(unsavedRequestIdToDelete)
    }
    override suspend fun saveUnsavedToExistingCollectionTx(
        collectionId: String,
        folderId: String,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    ) {
        promotionFailure?.let { throw it }
        saved[request.id] = request
        drafts.remove(unsavedRequestIdToDelete)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeBreakpointControl = FakeTestBreakpointControl()

    private fun createTestViewModel(
        executeUseCase: ExecuteClientApiRequestUseCase,
        breakpointControl: BreakpointControlPort = fakeBreakpointControl,
        collectionsRepository: TestCollectionsRepository = TestCollectionsRepository(),
        initialSettings: WorkspaceLayoutSettings = WorkspaceLayoutSettings(),
        layoutUseCases: Pair<GetWorkspaceLayoutUseCase, UpdateWorkspaceLayoutUseCase>? = null
    ): ApiStudioViewModel {
        val (getLayoutUseCase, updateLayoutUseCase) =
            layoutUseCases ?: createTestLayoutUseCases(initialSettings)
        val (observeApplicationSettings, updateApplicationSettings) = createTestApplicationSettingsUseCases()
        return ApiStudioViewModel(
            executeApiStudioRequestUseCase = ExecuteApiStudioRequestUseCase(
                executeRequest = executeUseCase,
                formatResponseBody = FormatResponseBodyUseCase(),
                scriptExecution = UnavailableScriptExecutionPort,
                ioDispatcher = testDispatcher
            ),
            observeProxyRuntimeStateUseCase = createTestObserveProxyRuntimeStateUseCase(),
            observeTrafficCaptureStateUseCase = createTestObserveTrafficCaptureStateUseCase(),
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
        assertEquals(SessionContext.None, state.sessionContext)
        assertNull(state.selectedRequestId)
        assertEquals(ExecutionState.IDLE, state.executionState)
        assertNull(state.responseInspection)
    }

    @Test
    fun `executeRequest maps queryParams headers cookies auth and updates response inspection`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://api.example.com/v1/users")
        viewModel.updateMethod(HttpMethod.POST)
        viewModel.updateHttpVersionPreference(HttpVersionPreference.HTTP_1_0)
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExecutionState.SUCCESS, state.executionState)
        assertEquals(HttpVersionPreference.HTTP_1_0, testExecutor.lastHttpVersionPreference)
        val response = assertNotNull(state.responseInspection)
        assertEquals(200, response.statusCode)
        assertEquals("OK", response.statusText)
        assertEquals("application/json", response.headers["content-type"])
        assertTrue(response.responseBody.contains("\"status\": \"success\""))
        assertEquals(
            ApplicationProtocol.Standard(StandardApplicationProtocol.HTTP_1_0),
            response.protocol,
        )
    }

    @Test
    fun `updateUrl and updateQueryParams sync bi-directionally`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        // 1. Typing URL with query parameters parses into queryParams list
        viewModel.updateUrl("http://localhost:9090/api/get?user=anant&role=admin")
        var state = viewModel.uiState.value
        assertEquals(2, state.editorState.queryParams.size)
        assertEquals("user" to "anant", state.editorState.queryParams[0].asPair())
        assertEquals("role" to "admin", state.editorState.queryParams[1].asPair())

        // 2. Modifying queryParams table reconstructs URL string
        viewModel.updateQueryParams(entries("user" to "anant", "role" to "superadmin", "page" to "1"))
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
        assertEquals("q" to "kotlin", parsedParams[0].asPair())
        assertEquals("category" to "mobile", parsedParams[1].asPair())
        assertEquals("page" to "1", parsedParams[2].asPair())
        assertEquals("limit" to "25", parsedParams[3].asPair())
        assertEquals("active" to "true", parsedParams[4].asPair())

        // 2. Modify multi-query list (update page, limit, and append sort parameter)
        val updatedMultiQueryList = entries(
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
        val trimmedMultiQueryList = entries(
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

        viewModel.updateMethod(HttpMethod.POST)
        viewModel.updateUrl("http://localhost:9090/api/post?debug=true")
        viewModel.updateHeaders(entries("Authorization" to "Bearer token_123", "X-Custom" to "HeaderValue"))
        viewModel.updateBodyState(
            RequestBodyState(
                mode = RequestBodyMode.JSON,
                payloadText = "{\"name\": \"KNet\"}"
            )
        )
        viewModel.updateAuthState(com.devuloopers.knet.ui.desktop.httppanel.model.AuthState(
            authType = com.devuloopers.knet.ui.desktop.httppanel.model.AuthType.BEARER_TOKEN,
            bearerToken = "secret_token_abc"
        ))
        viewModel.updateCookies(entries("session_id" to "sess_999"))
        viewModel.updatePreRequestScript("// pre-request")
        viewModel.updateTestScript("// test assertion")
        viewModel.updateActiveSubTab(com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab.HEADERS)

        val state = viewModel.uiState.value.editorState
        assertEquals(HttpMethod.POST, state.method)
        assertEquals("http://localhost:9090/api/post?debug=true", state.url)
        assertEquals(1, state.queryParams.size)
        assertEquals("debug" to "true", state.queryParams[0].asPair())
        assertEquals(2, state.headers.size)
        assertEquals("Authorization" to "Bearer token_123", state.headers[0].asPair())
        assertEquals("{\"name\": \"KNet\"}", state.bodyState.payloadText)
        assertEquals(RequestBodyMode.JSON, state.bodyState.mode)
        assertEquals(com.devuloopers.knet.ui.desktop.httppanel.model.AuthType.BEARER_TOKEN, state.authState.authType)
        assertEquals("secret_token_abc", state.authState.bearerToken)
        assertEquals(1, state.cookies.size)
        assertEquals("session_id" to "sess_999", state.cookies[0].asPair())
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
                proxyPort: Int?,
                httpVersionPreference: HttpVersionPreference,
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

        viewModel.importRequestSpec(spec, title = "Create Order")

        val state = viewModel.uiState.value
        assertEquals(HttpMethod.POST, state.editorState.method)
        assertEquals("https://api.example.com/v1/orders", state.editorState.url)
        assertEquals(listOf("Authorization" to "Bearer secret_token"), state.editorState.headers.map(KeyValueEntry::asPair))
        assertEquals(listOf("filter" to "active"), state.editorState.queryParams.map(KeyValueEntry::asPair))
        assertTrue(state.editorState.bodyState.payloadText.contains("\"item\": \"laptop\""))
        assertEquals(listOf("session" to "xyz123"), state.editorState.cookies.map(KeyValueEntry::asPair))
        assertEquals("Create Order", state.activeDocumentTitle)
        assertEquals(RequestNameOrigin.USER_DEFINED, state.activeDocumentNameOrigin)
    }

    @Test
    fun importRequestSpec_generatesMeaningfulHttpRequestName() = runTest(testDispatcher) {
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(TestHttpExecutor()))
        val spec = com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
            method = HttpMethod.GET,
            url = "https://api.example.com/account/user?expanded=true"
        )

        viewModel.importRequestSpec(spec)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/account/user", state.activeDocumentTitle)
        assertEquals(RequestNameOrigin.GENERATED, state.activeDocumentNameOrigin)
    }

    @Test
    fun explicitRequestName_isNotReplacedByLaterRequestEdits() = runTest(testDispatcher) {
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(TestHttpExecutor()))
        val spec = com.devuloopers.knet.domain.network.model.NetworkRequestSpec(
            method = HttpMethod.GET,
            url = "https://api.example.com/account/user"
        )

        viewModel.importRequestSpec(spec, title = "Load current account")
        viewModel.updateUrl("https://api.example.com/orders/latest")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Load current account", state.activeDocumentTitle)
        assertEquals(RequestNameOrigin.USER_DEFINED, state.activeDocumentNameOrigin)
    }

    @Test
    fun clearSessionContext_resetsEditorStateResponseInspectionAndSessionContextToNone() = runTest(testDispatcher) {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateUrl("https://stg-04astra.cnbc.com/graphql")
        viewModel.updateMethod(HttpMethod.POST)
        viewModel.updateBodyState(
            RequestBodyState(
                mode = RequestBodyMode.JSON,
                payloadText = "{\"query\": \"...\"}"
            )
        )
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.responseInspection)
        assertEquals("https://stg-04astra.cnbc.com/graphql", viewModel.uiState.value.editorState.url)

        viewModel.clearSessionContext()

        val state = viewModel.uiState.value
        assertEquals("", state.editorState.url)
        assertEquals(HttpMethod.GET, state.editorState.method)
        assertEquals("", state.editorState.bodyState.payloadText)
        assertNull(state.responseInspection)
        assertEquals(ExecutionState.IDLE, state.executionState)
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

        viewModel.closeTab(tabId)

        val state = viewModel.uiState.value
        assertNull(state.selectedRequestId)
        assertEquals("", state.editorState.url)
        assertEquals("", state.editorState.bodyState.payloadText)
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
        assertNull(state.selectedRequestId)
        assertEquals("", state.editorState.url)
        assertEquals(com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None, state.sessionContext)
    }

    @Test
    fun `updateBodyState retains strongly typed request body mode`() = runTest {
        val testExecutor = TestHttpExecutor()
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(testExecutor))

        viewModel.updateBodyState(RequestBodyState(mode = RequestBodyMode.GRAPHQL))

        val state = viewModel.uiState.value.editorState
        assertEquals(RequestBodyMode.GRAPHQL, state.bodyState.mode)
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
        viewModel.updateMethod(HttpMethod.POST)

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
                proxyPort: Int?,
                httpVersionPreference: HttpVersionPreference,
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
        viewModel.updateMethod(HttpMethod.GET)

        viewModel.executeRequest()
        testScheduler.advanceUntilIdle()

        assertEquals(ExecutionState.ERROR, viewModel.uiState.value.executionState)
        assertEquals("https://api.example.com/timeout-test", customInterceptionRepo.droppedUrl)
        assertEquals("GET", customInterceptionRepo.droppedMethod)
    }

    @Test
    fun `startup restores exact persisted document without waiting for sidebar streams`() = runTest {
        val repository = TestCollectionsRepository()
        repository.drafts["draft-restore"] = SavedApiRequest(
            id = "draft-restore",
            name = "Restored request",
            method = HttpMethod.POST,
            httpVersionPreference = HttpVersionPreference.HTTP_1_0,
            url = "https://api.example.com/restored",
            cookies = listOf(com.devuloopers.knet.domain.collection.model.RequestCookie("session", "abc")),
            auth = ApiRequestAuth.Basic("user", "password"),
            scripts = com.devuloopers.knet.domain.collection.model.ApiRequestScripts(
                preRequest = "before()",
                language = com.devuloopers.knet.scripting.model.ScriptLanguage.KOTLIN
            )
        )
        val viewModel = createTestViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(TestHttpExecutor()),
            collectionsRepository = repository,
            initialSettings = WorkspaceLayoutSettings(
                activeSessionId = SessionContextSerializer.serialize(
                    SessionContext.UnsavedDraft("draft-restore")
                )
            )
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SessionContext.UnsavedDraft("draft-restore"), state.sessionContext)
        assertEquals("Restored request", state.activeDocumentTitle)
        assertEquals("https://api.example.com/restored", state.editorState.url)
        assertEquals(HttpVersionPreference.HTTP_1_0, state.editorState.httpVersionPreference)
        assertEquals("abc", state.editorState.cookies.single().value)
        assertEquals("user", state.editorState.authState.basicUsername)
        assertEquals(com.devuloopers.knet.scripting.model.ScriptLanguage.KOTLIN, state.editorState.scriptLanguage)
    }

    @Test
    fun `startup restoration failure exits loading and exposes persistence error`() = runTest {
        val repository = object : WidgetPreferencesRepository {
            override val settingsFlow: Flow<WorkspaceLayoutSettings> = flow {
                throw IllegalStateException("Workspace storage is unavailable")
            }

            override suspend fun updateSettings(
                transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings,
            ) = Unit
        }
        val viewModel = createTestViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(TestHttpExecutor()),
            layoutUseCases = GetWorkspaceLayoutUseCase(repository) to UpdateWorkspaceLayoutUseCase(repository)
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRestoring)
        assertEquals("Workspace storage is unavailable", viewModel.uiState.value.persistenceErrorMessage)
    }

    @Test
    fun `promotion changes draft identity once and later edits stay in saved collection`() = runTest {
        val repository = TestCollectionsRepository()
        val viewModel = createTestViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(TestHttpExecutor()),
            collectionsRepository = repository
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateUrl("https://api.example.com/draft")
        val draftContext = viewModel.uiState.value.sessionContext as SessionContext.UnsavedDraft

        viewModel.saveRequestToCollection(
            requestName = "Saved request",
            mode = CollectionSaveMode.EXISTING_COLLECTION,
            selectedFolder = SidebarFolderItem(
                id = "folder-1",
                collectionId = "collection-1",
                name = "Requests"
            ),
            newCollectionName = ""
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val savedContext = viewModel.uiState.value.sessionContext as SessionContext.SavedRequest
        assertEquals("collection-1", savedContext.collectionId)
        assertEquals("folder-1", savedContext.folderId)
        assertTrue(draftContext.sessionId !in repository.drafts)
        assertEquals("Saved request", repository.saved[savedContext.requestId]?.name)

        viewModel.updateUrl("https://api.example.com/saved-edit")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.drafts.isEmpty())
        assertEquals("https://api.example.com/saved-edit", repository.saved[savedContext.requestId]?.url)
        assertEquals("Saved request", repository.saved[savedContext.requestId]?.name)
    }

    @Test
    fun `failed promotion keeps draft identity and exposes persistence failure`() = runTest {
        val repository = TestCollectionsRepository()
        val viewModel = createTestViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(TestHttpExecutor()),
            collectionsRepository = repository
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateUrl("https://api.example.com/draft")
        testDispatcher.scheduler.advanceUntilIdle()
        val draftContext = viewModel.uiState.value.sessionContext as SessionContext.UnsavedDraft
        repository.promotionFailure = IllegalStateException("Database is unavailable")

        viewModel.saveRequestToCollection(
            requestName = "Saved request",
            mode = CollectionSaveMode.EXISTING_COLLECTION,
            selectedFolder = SidebarFolderItem(
                id = "folder-1",
                collectionId = "collection-1",
                name = "Requests"
            ),
            newCollectionName = ""
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(draftContext, state.sessionContext)
        assertEquals(draftContext.sessionId, state.selectedRequestId)
        assertEquals("Database is unavailable", state.persistenceErrorMessage)
        assertTrue(draftContext.sessionId in repository.drafts)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `autosave retains disabled query header and cookie rows`() = runTest {
        val repository = TestCollectionsRepository()
        val viewModel = createTestViewModel(
            executeUseCase = ExecuteClientApiRequestUseCase(TestHttpExecutor()),
            collectionsRepository = repository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateUrl("https://api.example.com/items?enabled=yes")
        viewModel.updateQueryParams(
            listOf(
                KeyValueEntry("query-enabled", "enabled", "yes"),
                KeyValueEntry("query-disabled", "draft", "value", enabled = false)
            )
        )
        viewModel.updateHeaders(
            listOf(KeyValueEntry("header-disabled", "X-Draft", "secret", enabled = false))
        )
        viewModel.updateCookies(
            listOf(KeyValueEntry("cookie-disabled", "preview", "true", enabled = false))
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val saved = repository.drafts.values.single()
        assertEquals(false, saved.queryParameters.single { it.name == "draft" }.isEnabled)
        assertEquals(false, saved.headers.single().isEnabled)
        assertEquals(false, saved.cookies.single().isEnabled)
    }

    @Test
    fun `superseded execution cannot publish a stale result`() = runTest {
        val executor = object : HttpExecutor {
            override suspend fun execute(
                url: String,
                method: HttpMethod,
                headers: Map<String, String>,
                body: OutboundRequestBody,
                auth: ApiRequestAuth,
                proxyPort: Int?,
                httpVersionPreference: HttpVersionPreference,
            ): ExecutionResult {
                if (url.endsWith("/first")) {
                    try {
                        awaitCancellation()
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        return successfulResult("{\"source\":\"first\"}")
                    }
                }
                return successfulResult("{\"source\":\"second\"}")
            }

            override fun close() = Unit

            private fun successfulResult(body: String): ExecutionResult = ExecutionResult(
                statusCode = 200,
                statusText = "OK",
                headers = mapOf("Content-Type" to "application/json"),
                responseBody = body,
                timings = ExchangeTimings(totalMillis = 1L),
                responseSizeBytes = body.length.toLong()
            )
        }
        val viewModel = createTestViewModel(ExecuteClientApiRequestUseCase(executor))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateUrl("https://api.example.com/first")
        viewModel.executeRequest()
        testDispatcher.scheduler.runCurrent()
        viewModel.updateUrl("https://api.example.com/second")
        viewModel.executeRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        val response = assertNotNull(viewModel.uiState.value.responseInspection)
        assertTrue(response.responseBody.contains("second"))
        assertTrue(!response.responseBody.contains("first"))
    }
}
