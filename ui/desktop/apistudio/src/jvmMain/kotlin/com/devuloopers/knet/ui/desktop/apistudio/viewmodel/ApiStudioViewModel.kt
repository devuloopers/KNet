package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase
import com.devuloopers.knet.domain.clientNetwork.model.MimeType
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.*
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseSubTab
import com.devuloopers.knet.ui.desktop.apistudio.usecase.AutoSaveApiSessionUseCase
import com.devuloopers.knet.ui.desktop.apistudio.usecase.ExecuteScriptedApiRequestUseCase
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * Clean ViewModel managing UDF state for HTTP API request authoring, execution, and response inspection.
 *
 * SRP: Strictly presentation UDF state management. Script execution, network transport,
 * URL query string parsing, and Session serialization are delegated to domain Use Cases and utilities.
 *
 * Collection management & unsaved session tracking is owned by [CollectionsViewModel].
 *
 * @param executeScriptedUseCase Presentation UseCase for executing scripted HTTP API requests.
 * @param observeProxyEngineStateUseCase Use case for observing live KNet proxy engine state.
 * @param getWorkspaceLayoutUseCase Domain UseCase providing workspace layout settings stream.
 * @param saveWorkspaceLayoutUseCase Domain UseCase persisting updated workspace layout settings.
 * @param importRequestToStudioUseCase Domain UseCase validating and normalizing imported request specs.
 * @param syncBodyStateUseCase Presentation UseCase synchronizing payload mode switching and GraphQL state models.
 * @param autoSaveApiSessionUseCase Presentation UseCase auto-saving request state updates to Room DB.
 * @param ioDispatcher Coroutine dispatcher for background thread dispatching.
 */
class ApiStudioViewModel(
    private val executeScriptedUseCase: ExecuteScriptedApiRequestUseCase,
    observeProxyEngineStateUseCase: ObserveProxyEngineStateUseCase,
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val saveWorkspaceLayoutUseCase: SaveWorkspaceLayoutUseCase,
    private val importRequestToStudioUseCase: ImportRequestToStudioUseCase,
    private val syncBodyStateUseCase: SyncBodyStateUseCase? = null,
    private val autoSaveApiSessionUseCase: AutoSaveApiSessionUseCase? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    companion object {
        /**
         * Minimum visual loading duration in milliseconds to prevent single-frame visual flickering.
         */
        const val MIN_LOADING_DURATION_MS: Long = 200L
    }

    private val _uiState = MutableStateFlow(ApiStudioState())
    val uiState: StateFlow<ApiStudioState> = _uiState.asStateFlow()

    init {
        getWorkspaceLayoutUseCase.execute().onEach { settings ->
            val parsedSubTab = RequestSubTab.entries.find {
                it.name.equals(settings.activeRequestSubTab, ignoreCase = true)
            } ?: RequestSubTab.BODY

            val parsedScriptPhase = ScriptPhase.entries.find {
                it.name.equals(settings.activeScriptPhase, ignoreCase = true)
            } ?: ScriptPhase.PRE_REQUEST

            val parsedResponseSubTab = ResponseSubTab.entries.find {
                it.name.equals(settings.activeResponseSubTab, ignoreCase = true)
            } ?: ResponseSubTab.BODY

            _uiState.update { state ->
                state.copy(
                    sessionContext = SessionContextSerializer.deserialize(settings.activeSessionId),
                    editorState = state.editorState.copy(
                        activeSubTab = parsedSubTab,
                        activeScriptPhase = parsedScriptPhase,
                        activeResponseSubTab = parsedResponseSubTab,
                        scriptLanguage = com.devuloopers.knet.engine.script.api.ScriptLanguage.entries.find {
                            it.name.equals(settings.scriptLanguage, ignoreCase = true)
                        } ?: com.devuloopers.knet.engine.script.api.ScriptLanguage.JAVASCRIPT
                    )
                )
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Derived StateFlow of the active proxy port.
     */
    private val activeProxyPort: StateFlow<Int?> = observeProxyEngineStateUseCase.execute()
        .map { engineState -> (engineState as? ProxyEngineState.Running)?.port }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /**
     * Updates target URL in UDF state and synchronizes query parameters via [UrlQueryStringParser].
     */
    fun updateUrl(url: String) {
        val parsedParams = UrlQueryStringParser.parseQueryParams(url)
        _uiState.update { state ->
            state.copy(
                editorState = state.editorState.copy(
                    url = url,
                    queryParams = parsedParams
                )
            )
        }
        triggerAutoSave()
    }

    /**
     * Updates query parameters in UDF state and reconstructs the target URL via [UrlQueryStringParser].
     */
    fun updateQueryParams(queryParams: List<Pair<String, String>>) {
        _uiState.update { state ->
            val newUrl = UrlQueryStringParser.rebuildUrlWithQueryParams(state.editorState.url, queryParams)
            state.copy(
                editorState = state.editorState.copy(
                    url = newUrl,
                    queryParams = queryParams
                )
            )
        }
        triggerAutoSave()
    }

    /**
     * Updates HTTP method in UDF state.
     */
    fun updateMethod(method: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map {
                if (it.id == state.activeTabId) it.copy(method = method) else it
            }
            state.copy(
                editorState = state.editorState.copy(method = method),
                tabs = updatedTabs
            )
        }
        triggerAutoSave()
    }

    /**
     * Updates request headers in UDF state.
     */
    fun updateHeaders(headers: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(headers = headers)) }
        triggerAutoSave()
    }

    /**
     * Updates request body payload string in UDF state.
     */
    fun updateBodyPayload(bodyPayload: String) {
        _uiState.update {
            val updatedBodyState = it.editorState.bodyState.copy(payloadText = bodyPayload)
            it.copy(editorState = it.editorState.copy(bodyState = updatedBodyState))
        }
        triggerAutoSave()
    }

    /**
     * Updates strongly-typed body state configuration in UDF state.
     */
    fun updateBodyState(bodyState: RequestBodyState) {
        _uiState.update {
            val hydratedState = syncBodyStateUseCase?.ensureHydrated(bodyState) ?: bodyState
            it.copy(editorState = it.editorState.copy(bodyState = hydratedState))
        }
        triggerAutoSave()
    }

    /**
     * Updates strongly-typed body mode in UDF state, synchronizing GraphQL state if needed.
     */
    fun updateBodyMode(mode: RequestBodyMode) {
        _uiState.update {
            val currentBodyState = it.editorState.bodyState
            val updatedBodyState = syncBodyStateUseCase?.switchMode(currentBodyState, mode)
                ?: currentBodyState.copy(mode = mode)
            it.copy(editorState = it.editorState.copy(bodyState = updatedBodyState))
        }
        triggerAutoSave()
    }

    /**
     * Updates structured [GraphQlState] in UDF state and serializes it back to transport payload text.
     */
    fun updateGraphQlState(graphQlState: GraphQlState) {
        _uiState.update {
            val currentBodyState = it.editorState.bodyState
            val updatedBodyState = syncBodyStateUseCase?.updateGraphQlState(currentBodyState, graphQlState)
                ?: currentBodyState.copy(graphQlState = graphQlState)
            it.copy(editorState = it.editorState.copy(bodyState = updatedBodyState))
        }
        triggerAutoSave()
    }

    /**
     * Updates cookies in UDF state.
     */
    fun updateCookies(cookies: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(cookies = cookies)) }
        triggerAutoSave()
    }

    /**
     * Updates strongly-typed authentication state in UDF state.
     */
    fun updateAuthState(authState: AuthState) {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(
                    authState = authState,
                    authType = authState.authType.label,
                    authToken = authState.bearerToken
                )
            )
        }
        triggerAutoSave()
    }

    /**
     * Updates pre-request and post-response script contents in UDF state.
     */
    fun updateScripts(preRequestScript: String, testScript: String) {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(
                    preRequestScript = preRequestScript,
                    testScript = testScript
                )
            )
        }
        triggerAutoSave()
    }

    fun updatePreRequestScript(preRequestScript: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(preRequestScript = preRequestScript))
        }
        triggerAutoSave()
    }

    fun updateTestScript(testScript: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(testScript = testScript))
        }
        triggerAutoSave()
    }

    private fun triggerAutoSave() {
        val currentState = _uiState.value
        val context = currentState.sessionContext
        val editorState = currentState.editorState
        if (context is SessionContext.None) return
        viewModelScope.launch(ioDispatcher) {
            autoSaveApiSessionUseCase?.execute(
                sessionContext = context,
                editorState = editorState,
                onLinkedIdAssigned = { id, title ->
                    updateLinkedUnsavedId(id, title)
                }
            )
        }
    }

    /**
     * Updates active sub-tab selection in UDF state.
     */
    /**
     * Updates active sub-tab selection in UDF state.
     */
    fun updateActiveSubTab(subTab: RequestSubTab) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeSubTab = subTab)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = getWorkspaceLayoutUseCase.execute().firstOrNull()
                ?: WorkspaceLayoutSettings()
            saveWorkspaceLayoutUseCase.execute(currentSettings.copy(activeRequestSubTab = subTab.name))
        }
    }

    /**
     * Updates active script editing phase in UDF state.
     */
    fun updateActiveScriptPhase(phase: ScriptPhase) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeScriptPhase = phase)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = getWorkspaceLayoutUseCase.execute().firstOrNull()
                ?: WorkspaceLayoutSettings()
            saveWorkspaceLayoutUseCase.execute(currentSettings.copy(activeScriptPhase = phase.name))
        }
    }

    fun updateScriptLanguage(language: com.devuloopers.knet.engine.script.api.ScriptLanguage) {
        _uiState.update { it.copy(editorState = it.editorState.copy(scriptLanguage = language)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = getWorkspaceLayoutUseCase.execute().firstOrNull()
                ?: WorkspaceLayoutSettings()
            saveWorkspaceLayoutUseCase.execute(currentSettings.copy(scriptLanguage = language.name))
        }
    }

    fun updateActiveResponseSubTab(tab: ResponseSubTab) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeResponseSubTab = tab)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = getWorkspaceLayoutUseCase.execute().firstOrNull()
                ?: WorkspaceLayoutSettings()
            saveWorkspaceLayoutUseCase.execute(currentSettings.copy(activeResponseSubTab = tab.name))
        }
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val remainingTabs = state.tabs.filterNot { it.id == tabId }
            val isClosingActive = state.activeTabId == tabId || state.editorState.linkedUnsavedId == tabId

            if (isClosingActive || remainingTabs.isEmpty()) {
                state.copy(
                    tabs = remainingTabs,
                    activeTabId = "",
                    editorState = RequestEditorState(
                        url = "",
                        method = "GET",
                        queryParams = emptyList(),
                        headers = RequestEditorDefaults.DEFAULT_HEADERS,
                        cookies = emptyList(),
                        preRequestScript = "",
                        testScript = "",
                        activeSubTab = state.editorState.activeSubTab,
                        activeScriptPhase = state.editorState.activeScriptPhase,
                        activeResponseSubTab = state.editorState.activeResponseSubTab,
                        linkedUnsavedId = null,
                        sessionType = SessionType.NONE
                    ),
                    executionState = ExecutionState.IDLE,
                    responsePresentation = null,
                    errorMessage = null,
                    sessionContext = SessionContext.None
                )
            } else {
                state.copy(tabs = remainingTabs)
            }
        }
        if (_uiState.value.activeTabId.isBlank()) {
            saveSessionContextToPreferences(SessionContext.None)
        }
    }

    /**
     * Imports a captured strongly-typed [com.devuloopers.knet.domain.network.model.NetworkRequestSpec] into a new unsaved session draft tab in API Studio.
     *
     * @param spec Strongly-typed domain network request specification.
     * @param title Optional custom tab display title.
     */
    fun importRequestSpec(
        spec: NetworkRequestSpec,
        title: String? = null
    ): String {
        val importedResult = importRequestToStudioUseCase.execute(spec, title)
        val normalizedSpec = importedResult.spec
        val sessionUuid = Uuid.random().toString()
        val newTab = RequestTab(sessionUuid, importedResult.displayTitle, method = normalizedSpec.methodString)
        val mappedAuthState = normalizedSpec.auth.toAuthState()

        _uiState.update { state ->
            val hydratedBodyState = RequestBodyState.fromPayload(
                headers = normalizedSpec.headers,
                rawBody = normalizedSpec.bodyPayload
            )
            val importedEditorState = RequestEditorState(
                method = normalizedSpec.methodString,
                url = normalizedSpec.url,
                headers = normalizedSpec.headers.sanitizeTransportHeaders(),
                queryParams = normalizedSpec.queryParams,
                bodyState = hydratedBodyState,
                cookies = normalizedSpec.cookies,
                authState = mappedAuthState,
                authType = mappedAuthState.authType.label,
                authToken = mappedAuthState.bearerToken,
                activeSubTab = state.editorState.activeSubTab,
                activeScriptPhase = state.editorState.activeScriptPhase,
                activeResponseSubTab = state.editorState.activeResponseSubTab,
                linkedUnsavedId = sessionUuid,
                sessionType = SessionType.UNSAVED_DRAFT
            )
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = sessionUuid,
                editorState = importedEditorState,
                sessionContext = SessionContext.UnsavedDraft(sessionUuid)
            )
        }
        saveSessionContextToPreferences(SessionContext.UnsavedDraft(sessionUuid))
        return sessionUuid
    }

    /**
     * Updates active tab's title and linked unsaved session ID.
     */
    fun updateLinkedUnsavedId(unsavedId: String?, title: String) {
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

    fun setUnsavedDraftSession(sessionId: String) {
        val context = SessionContext.UnsavedDraft(sessionId)
        _uiState.update { it.copy(sessionContext = context) }
        saveSessionContextToPreferences(context)
    }

    fun setSavedRequestSession(requestId: String, collectionId: String, folderId: String) {
        val context = SessionContext.SavedRequest(requestId, collectionId, folderId)
        _uiState.update { it.copy(sessionContext = context) }
        saveSessionContextToPreferences(context)
    }

    fun clearSessionContext() {
        val context = SessionContext.None
        _uiState.update { state ->
            state.copy(
                tabs = emptyList(),
                activeTabId = "",
                editorState = RequestEditorState(
                    url = "",
                    method = "GET",
                    queryParams = emptyList(),
                    headers = RequestEditorDefaults.DEFAULT_HEADERS,
                    cookies = emptyList(),
                    preRequestScript = "",
                    testScript = "",
                    activeSubTab = state.editorState.activeSubTab,
                    activeScriptPhase = state.editorState.activeScriptPhase,
                    activeResponseSubTab = state.editorState.activeResponseSubTab,
                    linkedUnsavedId = null,
                    sessionType = SessionType.NONE
                ),
                executionState = ExecutionState.IDLE,
                responsePresentation = null,
                errorMessage = null,
                sessionContext = context
            )
        }
        saveSessionContextToPreferences(context)
    }

    fun clearSession() = clearSessionContext()

    private fun saveSessionContextToPreferences(context: SessionContext) {
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = getWorkspaceLayoutUseCase.execute().firstOrNull()
                ?: WorkspaceLayoutSettings()
            saveWorkspaceLayoutUseCase.execute(
                currentSettings.copy(
                    activeSessionId = SessionContextSerializer.serialize(
                        context
                    )
                )
            )
        }
    }

    fun clearResponse() {
        _uiState.update {
            it.copy(
                executionState = ExecutionState.IDLE,
                responsePresentation = null,
                errorMessage = null
            )
        }
    }

    /**
     * Executes the active HTTP API request by delegating to [ExecuteScriptedApiRequestUseCase].
     */
    fun executeRequest() {
        val currentEditor = _uiState.value.editorState
        _uiState.update { it.copy(executionState = ExecutionState.EXECUTING, errorMessage = null) }

        val executionStartTime = System.currentTimeMillis()

        viewModelScope.launch {
            try {
                val execTuple = executeScriptedUseCase.execute(
                    editorState = currentEditor,
                    proxyPort = activeProxyPort.value
                )

                val presentation = ResponsePresentation(
                    statusCode = execTuple.result.statusCode,
                    statusText = execTuple.result.statusText,
                    durationMs = execTuple.result.latencyMs,
                    sizeBytes = execTuple.result.responseSizeBytes,
                    mimeType = if (execTuple.mimeType != MimeType.UNKNOWN) execTuple.mimeType.value else "text/plain",
                    headers = execTuple.result.headers,
                    cookies = execTuple.result.cookies,
                    body = execTuple.formattedBody,
                    testResults = execTuple.testResults,
                    consoleLogs = execTuple.consoleLogs,
                    failureReason = execTuple.result.failureReason
                )

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
