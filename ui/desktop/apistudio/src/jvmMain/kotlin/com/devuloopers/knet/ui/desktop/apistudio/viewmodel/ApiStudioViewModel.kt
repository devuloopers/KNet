package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestTab
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponsePresentation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * ViewModel managing UDF state for HTTP API request authoring, execution, and response inspection.
 *
 * Observes proxy engine state and automatically routes requests through KNet's
 * local Netty proxy when it is active (`proxyPort != null`). When the proxy is
 * off, requests are executed directly via Ktor with no Traffic recording.
 *
 * Collection management & unsaved session tracking is owned by [CollectionsViewModel].
 *
 * @param executeUseCase Use case for executing client HTTP API requests.
 * @param formatResponseBodyUseCase Use case for formatting raw response bodies.
 * @param observeProxyEngineStateUseCase Use case for observing the live KNet proxy engine state.
 * @param ioDispatcher Coroutine dispatcher for network execution I/O.
 */
public class ApiStudioViewModel(
    private val executeUseCase: ExecuteClientApiRequestUseCase,
    private val formatResponseBodyUseCase: FormatResponseBodyUseCase,
    observeProxyEngineStateUseCase: ObserveProxyEngineStateUseCase,
    private val widgetPreferencesRepository: com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    companion object {
        /**
         * Minimum visual loading duration in milliseconds to prevent single-frame
         * visual flickering on ultra-fast responses (< 200ms).
         */
        public const val MIN_LOADING_DURATION_MS: Long = 200L
    }

    private val _uiState = MutableStateFlow(ApiStudioState())
    public val uiState: StateFlow<ApiStudioState> = _uiState.asStateFlow()

    init {
        val repository = widgetPreferencesRepository
        if (repository != null) {
            runCatching {
                runBlocking(ioDispatcher) {
                    val initialSettings = repository.settingsFlow.first()
                    _uiState.update { state ->
                        state.copy(
                            sessionContext = deserializeSessionContext(initialSettings.activeSessionId),
                            editorState = state.editorState.copy(
                                activeSubTab = initialSettings.activeRequestSubTab,
                                activeScriptPhase = initialSettings.activeScriptPhase,
                                activeResponseSubTab = initialSettings.activeResponseSubTab,
                                scriptLanguage = initialSettings.scriptLanguage
                            )
                        )
                    }
                }
            }

            repository.settingsFlow.onEach { settings ->
                _uiState.update { state ->
                    state.copy(
                        sessionContext = deserializeSessionContext(settings.activeSessionId),
                        editorState = state.editorState.copy(
                            activeSubTab = settings.activeRequestSubTab,
                            activeScriptPhase = settings.activeScriptPhase,
                            activeResponseSubTab = settings.activeResponseSubTab,
                            scriptLanguage = settings.scriptLanguage
                        )
                    )
                }
            }.launchIn(viewModelScope)
        }
    }

    /**
     * Deserializes a raw stored session string back into a [SessionContext].
     *
     * Encoding format:
     * - `""` or unrecognized → [SessionContext.None]
     * - `"unsaved:<sessionId>"` → [SessionContext.UnsavedDraft]
     * - `"saved:<requestId>:<collectionId>:<folderId>"` → [SessionContext.SavedRequest]
     */
    private fun deserializeSessionContext(raw: String): com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext {
        if (raw.isBlank()) return com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None
        return when {
            raw.startsWith("unsaved:") -> {
                val sessionId = raw.removePrefix("unsaved:")
                com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.UnsavedDraft(sessionId)
            }
            raw.startsWith("saved:") -> {
                val parts = raw.removePrefix("saved:").split(":")
                if (parts.size >= 3) {
                    com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.SavedRequest(
                        requestId = parts[0],
                        collectionId = parts[1],
                        folderId = parts[2]
                    )
                } else {
                    com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None
                }
            }
            else -> com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None
        }
    }

    /**
     * Serializes a [SessionContext] into a compact string for DataStore persistence.
     */
    private fun serializeSessionContext(context: com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext): String {
        return when (context) {
            is com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None -> ""
            is com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.UnsavedDraft -> "unsaved:${context.sessionId}"
            is com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.SavedRequest -> "saved:${context.requestId}:${context.collectionId}:${context.folderId}"
        }
    }

    /**
     * Derived StateFlow of the active proxy port.
     * Emits the port carried by [ProxyEngineState.Running] when the proxy is active, null otherwise.
     */
    private val activeProxyPort: StateFlow<Int?> = observeProxyEngineStateUseCase.execute()
        .map { engineState -> (engineState as? ProxyEngineState.Running)?.port }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /**
     * Updates the target request URL string in UDF state and automatically synchronizes
     * parsed query parameters into the Params table state.
     *
     * @param url The raw URL string input from the URL bar (e.g. "http://localhost:9090/api/get?foo=bar").
     */
    public fun updateUrl(url: String) {
        val parsedParams = parseQueryParamsFromUrl(url)
        _uiState.update { state ->
            state.copy(
                editorState = state.editorState.copy(
                    url = url,
                    queryParams = parsedParams
                )
            )
        }
    }

    /**
     * Updates query parameter key-value pairs in UDF state and automatically reconstructs
     * the target URL string in the URL bar to reflect parameter changes in real time.
     *
     * @param queryParams Key-value pairs representing request query parameters.
     */
    public fun updateQueryParams(queryParams: List<Pair<String, String>>) {
        _uiState.update { state ->
            val currentUrl = state.editorState.url
            val baseUrl = if (currentUrl.contains("?")) currentUrl.substringBefore("?") else currentUrl
            val activeParams = queryParams.filter { it.first.isNotBlank() }
            val newUrl = if (activeParams.isNotEmpty()) {
                val queryString = activeParams.joinToString("&") { "${it.first}=${it.second}" }
                "$baseUrl?$queryString"
            } else {
                baseUrl
            }
            state.copy(
                editorState = state.editorState.copy(
                    url = newUrl,
                    queryParams = queryParams
                )
            )
        }
    }

    /**
     * Helper function parsing a raw URL string into key-value query parameter pairs.
     *
     * @param url Target URL string containing optional `?key=value` query string.
     * @return List of key-value pairs extracted from query string.
     */
    private fun parseQueryParamsFromUrl(url: String): List<Pair<String, String>> {
        if (!url.contains("?")) return emptyList()
        val queryString = url.substringAfter("?").substringBefore("#")
        if (queryString.isBlank()) return emptyList()
        return queryString.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            val key = parts.getOrNull(0)?.trim() ?: ""
            if (key.isNotBlank()) {
                val value = parts.getOrNull(1)?.trim() ?: ""
                key to value
            } else null
        }
    }

    /**
     * Updates HTTP method in UDF state and synchronizes active tab header display.
     *
     * @param method Selected HTTP method string (GET, POST, PUT, DELETE, etc.).
     */
    public fun updateMethod(method: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map {
                if (it.id == state.activeTabId) it.copy(method = method) else it
            }
            state.copy(
                editorState = state.editorState.copy(method = method),
                tabs = updatedTabs
            )
        }
    }

    /**
     * Updates request headers in UDF state.
     *
     * @param headers List of header key-value pairs.
     */
    public fun updateHeaders(headers: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(headers = headers)) }
    }

    /**
     * Updates request body payload text content in UDF state.
     *
     * @param bodyPayload Raw request body string content.
     */
    public fun updateBodyPayload(bodyPayload: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(bodyPayload = bodyPayload)) }
    }

    /**
     * Updates request body type (JSON, Form, Raw, None) in UDF state.
     *
     * @param bodyType Selected body mode representation string.
     */
    public fun updateBodyType(bodyType: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(bodyType = bodyType)) }
    }

    /**
     * Updates authentication configuration type and credential token in UDF state.
     *
     * @param authType Selected authentication type (No Auth, Bearer Token, Basic Auth).
     * @param authToken Credential token string.
     */
    public fun updateAuth(authType: String, authToken: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(authType = authType, authToken = authToken))
        }
    }

    /**
     * Updates request cookies in UDF state.
     *
     * @param cookies List of cookie key-value pairs.
     */
    public fun updateCookies(cookies: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(cookies = cookies)) }
    }

    /**
     * Updates pre-request and test scripts in UDF state.
     *
     * @param preRequestScript Script code executed before request execution.
     * @param testScript Script code executed after response receipt.
     */
    public fun updateScripts(preRequestScript: String, testScript: String) {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(
                    preRequestScript = preRequestScript,
                    testScript = testScript
                )
            )
        }
    }

    public fun updatePreRequestScript(preRequestScript: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(preRequestScript = preRequestScript))
        }
    }

    public fun updateTestScript(testScript: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(testScript = testScript))
        }
    }

    /**
     * Updates the active sub-tab selection (PARAMS, AUTH, HEADERS, BODY, COOKIES, SCRIPTS) in UDF state.
     *
     * @param subTabName Name of the active sub-tab.
     */
    public fun updateActiveSubTab(subTabName: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeSubTab = subTabName)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = widgetPreferencesRepository?.settingsFlow?.firstOrNull() ?: com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings()
            widgetPreferencesRepository?.saveSettings(currentSettings.copy(activeRequestSubTab = subTabName))
        }
    }

    /**
     * Updates the active tab's title and linked unsaved session ID.
     */
    public fun updateLinkedUnsavedId(unsavedId: String?, title: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(title = title) else tab
            }
            state.copy(
                tabs = updatedTabs,
                editorState = state.editorState.copy(linkedUnsavedId = unsavedId)
            )
        }
    }

    /**
     * Synchronously returns the current linked unsaved ID or generates and sets a new one.
     */
    public fun getOrGenerateLinkedUnsavedId(): String {
        val currentId = _uiState.value.editorState.linkedUnsavedId
        if (currentId != null) return currentId

        val newId = "unsaved_${System.currentTimeMillis()}"
        _uiState.update { state ->
            state.copy(editorState = state.editorState.copy(linkedUnsavedId = newId))
        }
        return newId
    }

    /**
     * Clears current HTTP response presentation state and resets execution status to IDLE.
     */
    public fun clearResponse() {
        _uiState.update { state ->
            state.copy(
                responsePresentation = null,
                executionState = ExecutionState.IDLE,
                errorMessage = null
            )
        }
    }

    public fun selectEnvironment(envName: String) {
        _uiState.update { it.copy(selectedEnvironment = envName) }
    }

    /**
     * Sets the active session context to an unsaved draft session.
     *
     * Persists the session context to DataStore so it is restored on next app launch.
     *
     * @param sessionId The unique ID of the unsaved draft session (e.g. `"unsaved_1234567890"`).
     */
    public fun setUnsavedDraftSession(sessionId: String) {
        val context = com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.UnsavedDraft(sessionId)
        _uiState.update { it.copy(sessionContext = context) }
        persistSessionContext(context)
    }

    /**
     * Sets the active session context to a saved collection request.
     *
     * Persists the session context to DataStore so it is restored on next app launch.
     *
     * @param requestId The unique ID of the saved request record.
     * @param collectionId The ID of the parent collection.
     * @param folderId The ID of the parent folder.
     */
    public fun setSavedRequestSession(requestId: String, collectionId: String, folderId: String) {
        val context = com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.SavedRequest(
            requestId = requestId,
            collectionId = collectionId,
            folderId = folderId
        )
        _uiState.update { it.copy(sessionContext = context) }
        persistSessionContext(context)
    }

    /**
     * Clears the active session context, returning the editor to a blank [SessionContext.None] state.
     *
     * Persists the cleared state to DataStore.
     */
    public fun clearSession() {
        _uiState.update { it.copy(sessionContext = com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None) }
        persistSessionContext(com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext.None)
    }

    /**
     * Persists the serialized [SessionContext] string to DataStore.
     */
    private fun persistSessionContext(context: com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext) {
        viewModelScope.launch(ioDispatcher) {
            val repository = widgetPreferencesRepository ?: return@launch
            val currentSettings = repository.settingsFlow.firstOrNull() ?: com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings()
            repository.saveSettings(currentSettings.copy(activeSessionId = serializeSessionContext(context)))
        }
    }

    public fun updateActiveScriptPhase(phase: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeScriptPhase = phase)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = widgetPreferencesRepository?.settingsFlow?.firstOrNull() ?: com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings()
            widgetPreferencesRepository?.saveSettings(currentSettings.copy(activeScriptPhase = phase))
        }
    }

    public fun updateScriptLanguage(language: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(scriptLanguage = language)) }
        viewModelScope.launch(ioDispatcher) {
            val repository = widgetPreferencesRepository ?: return@launch
            val currentSettings = repository.settingsFlow.firstOrNull() ?: com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings()
            repository.saveSettings(currentSettings.copy(scriptLanguage = language))
        }
    }

    public fun updateActiveResponseSubTab(tab: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeResponseSubTab = tab)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = widgetPreferencesRepository?.settingsFlow?.firstOrNull() ?: com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings()
            widgetPreferencesRepository?.saveSettings(currentSettings.copy(activeResponseSubTab = tab))
        }
    }

    public fun selectTab(tabId: String) {
        _uiState.update { it.copy(activeTabId = tabId) }
    }

    public fun closeTab(tabId: String) {
        _uiState.update { state ->
            val remainingTabs = state.tabs.filterNot { it.id == tabId }
            val nextActiveId = if (state.activeTabId == tabId) {
                remainingTabs.lastOrNull()?.id ?: "tab_1"
            } else state.activeTabId

            val finalTabs = remainingTabs.ifEmpty {
                listOf(RequestTab("tab_1", "New Request"))
            }

            state.copy(tabs = finalTabs, activeTabId = nextActiveId)
        }
    }

    public fun openNewTab() {
        val newId = "tab_${System.currentTimeMillis()}"
        val newTab = RequestTab(newId, "New Request")
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newId,
                editorState = com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState(
                    activeSubTab = state.editorState.activeSubTab,
                    activeScriptPhase = state.editorState.activeScriptPhase,
                    activeResponseSubTab = state.editorState.activeResponseSubTab
                )
            )
        }
    }

    public fun executeRequest() {
        val currentEditor = _uiState.value.editorState
        _uiState.update { it.copy(executionState = ExecutionState.EXECUTING, errorMessage = null) }

        val executionStartTime = System.currentTimeMillis()

        viewModelScope.launch {
            try {
                val execTuple = withContext(ioDispatcher) {
                    var effectiveUrl = currentEditor.url
                    var headerMap = currentEditor.headers.toMap()
                    var queryParamMap = currentEditor.queryParams.toMap()
                    var effectiveBody = currentEditor.bodyPayload

                    val environmentStore = com.devuloopers.knet.engine.script.api.EnvironmentStore()
                    val uiConsoleLogs = mutableListOf<String>()

                    val targetLanguage = if (currentEditor.scriptLanguage.equals("KOTLIN", ignoreCase = true)) {
                        com.devuloopers.knet.engine.script.api.ScriptLanguage.KOTLIN
                    } else {
                        com.devuloopers.knet.engine.script.api.ScriptLanguage.JAVASCRIPT
                    }

                    // Pre-request Script Execution
                    if (currentEditor.preRequestScript.isNotBlank()) {
                        val scriptReq = com.devuloopers.knet.engine.script.api.ScriptRequestModel(
                            url = effectiveUrl,
                            method = currentEditor.method,
                            headers = headerMap.toMutableMap(),
                            queryParams = queryParamMap.toMutableMap(),
                            body = effectiveBody
                        )
                        val preScriptResult = com.devuloopers.knet.engine.script.runtime.ScriptRuntime.execute(
                            language = targetLanguage,
                            code = currentEditor.preRequestScript,
                            request = scriptReq,
                            response = null,
                            environment = environmentStore
                        )
                        when (preScriptResult) {
                            is com.devuloopers.knet.engine.script.api.ScriptExecutionResult.Success -> {
                                uiConsoleLogs.addAll(preScriptResult.logs)
                                effectiveUrl = preScriptResult.request.url
                                headerMap = preScriptResult.request.headers.toMap()
                                queryParamMap = preScriptResult.request.queryParams.toMap()
                                effectiveBody = preScriptResult.request.body
                            }
                            is com.devuloopers.knet.engine.script.api.ScriptExecutionResult.Error -> {
                                uiConsoleLogs.add("[Pre-request Error] ${preScriptResult.message}")
                            }
                        }
                    }

                    val authConfig = when (currentEditor.authType.lowercase()) {
                        "bearer token", "bearer" -> com.devuloopers.knet.domain.collection.model.ApiRequestAuth.Bearer(
                            currentEditor.authToken
                        )

                        "api key", "apikey" -> com.devuloopers.knet.domain.collection.model.ApiRequestAuth.ApiKey(value = currentEditor.authToken)
                        "basic auth", "basic" -> {
                            val parts = currentEditor.authToken.split(":", limit = 2)
                            com.devuloopers.knet.domain.collection.model.ApiRequestAuth.Basic(
                                username = parts.getOrNull(0) ?: "",
                                password = parts.getOrNull(1) ?: ""
                            )
                        }

                        else -> com.devuloopers.knet.domain.collection.model.ApiRequestAuth.None
                    }

                    val httpMethodEnum = try {
                        HttpMethod.valueOf(currentEditor.method.uppercase())
                    } catch (_: Exception) {
                        HttpMethod.GET
                    }

                    val bodyTypeEnum = when (currentEditor.bodyType.uppercase()) {
                        "JSON" -> RequestBodyType.JSON
                        "XML" -> RequestBodyType.XML
                        "FORM", "FORM_DATA" -> RequestBodyType.FORM_DATA
                        "GRAPHQL" -> RequestBodyType.GRAPHQL
                        "RAW", "RAW_TEXT" -> RequestBodyType.RAW_TEXT
                        else -> RequestBodyType.NONE
                    }
                    val cookieMap = currentEditor.cookies.toMap()
                    val res = executeUseCase(
                        url = effectiveUrl,
                        method = httpMethodEnum,
                        headers = headerMap,
                        queryParams = queryParamMap,
                        cookies = cookieMap,
                        body = if (bodyTypeEnum != RequestBodyType.NONE) effectiveBody else "",
                        bodyType = bodyTypeEnum,
                        auth = authConfig,
                        proxyPort = activeProxyPort.value
                    )

                    val mime = com.devuloopers.knet.domain.util.MimeTypeUtils.extractFromHeaders(res.headers)
                    val bodyText = formatResponseBodyUseCase.execute(
                        rawBody = res.responseBody,
                        mimeType = mime
                    )

                    // Post-response Test Script Execution
                    var uiTestResults = emptyList<com.devuloopers.knet.ui.desktop.apistudio.model.TestResult>()

                    if (currentEditor.testScript.isNotBlank() && res.failureReason == null && res.statusCode !in setOf(502, 503, 504)) {
                        val scriptReq = com.devuloopers.knet.engine.script.api.ScriptRequestModel(
                            url = currentEditor.url,
                            method = currentEditor.method,
                            headers = headerMap.toMutableMap(),
                            queryParams = queryParamMap.toMutableMap(),
                            body = currentEditor.bodyPayload
                        )
                        val scriptResp = com.devuloopers.knet.engine.script.api.ScriptResponseModel(
                            statusCode = res.statusCode,
                            statusText = res.statusText,
                            latencyMs = res.latencyMs,
                            responseSizeBytes = res.responseSizeBytes,
                            headers = res.headers,
                            body = res.responseBody
                        )
                        val scriptResult = com.devuloopers.knet.engine.script.runtime.ScriptRuntime.execute(
                            language = targetLanguage,
                            code = currentEditor.testScript,
                            request = scriptReq,
                            response = scriptResp,
                            environment = environmentStore
                        )

                        when (scriptResult) {
                            is com.devuloopers.knet.engine.script.api.ScriptExecutionResult.Success -> {
                                uiTestResults = scriptResult.testResults.map {
                                    com.devuloopers.knet.ui.desktop.apistudio.model.TestResult(
                                        name = it.name,
                                        passed = it.passed,
                                        errorMessage = if (it.passed) null else it.errorMessage
                                    )
                                }
                                uiConsoleLogs.addAll(scriptResult.logs)
                            }
                            is com.devuloopers.knet.engine.script.api.ScriptExecutionResult.Error -> {
                                uiTestResults = listOf(
                                    com.devuloopers.knet.ui.desktop.apistudio.model.TestResult(
                                        name = "Script Execution Error",
                                        passed = false,
                                        errorMessage = scriptResult.message
                                    )
                                )
                                uiConsoleLogs.add("[Test Script Error] ${scriptResult.message}")
                            }
                        }
                    }

                    ExecutionResultTuple(res, bodyText, mime, uiTestResults, uiConsoleLogs)
                }

                val presentation = ResponsePresentation(
                    statusCode = execTuple.result.statusCode,
                    statusText = execTuple.result.statusText,
                    durationMs = execTuple.result.latencyMs,
                    sizeBytes = execTuple.result.responseSizeBytes,
                    mimeType = if (execTuple.mimeType != com.devuloopers.knet.domain.clientNetwork.model.MimeType.UNKNOWN) execTuple.mimeType.value else "text/plain",
                    headers = execTuple.result.headers,
                    cookies = execTuple.result.cookies,
                    body = execTuple.formattedBody,
                    testResults = execTuple.testResults,
                    consoleLogs = execTuple.consoleLogs,
                    failureReason = execTuple.result.failureReason
                )

                // Enforce minimum visual loading duration to prevent single-frame flickering
                val elapsedMs = System.currentTimeMillis() - executionStartTime
                if (elapsedMs < MIN_LOADING_DURATION_MS) {
                    delay((MIN_LOADING_DURATION_MS - elapsedMs).milliseconds)
                }

                _uiState.update {
                    it.copy(
                        executionState = if (execTuple.result.isSuccess) ExecutionState.SUCCESS else ExecutionState.ERROR,
                        responsePresentation = presentation,
                        errorMessage = execTuple.result.errorMessage
                    )
                }
            } catch (e: Exception) {
                val elapsedMs = System.currentTimeMillis() - executionStartTime
                if (elapsedMs < MIN_LOADING_DURATION_MS) {
                    delay((MIN_LOADING_DURATION_MS - elapsedMs).milliseconds)
                }

                _uiState.update {
                    it.copy(
                        executionState = ExecutionState.ERROR,
                        errorMessage = e.message ?: "Failed to execute request"
                    )
                }
            }
        }
    }
}

private data class ExecutionResultTuple(
    val result: com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult,
    val formattedBody: String,
    val mimeType: com.devuloopers.knet.domain.clientNetwork.model.MimeType,
    val testResults: List<com.devuloopers.knet.ui.desktop.apistudio.model.TestResult>,
    val consoleLogs: List<String>
)
