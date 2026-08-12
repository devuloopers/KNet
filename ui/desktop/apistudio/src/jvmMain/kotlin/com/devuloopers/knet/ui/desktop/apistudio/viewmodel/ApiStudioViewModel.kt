package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase
import com.devuloopers.knet.domain.clientNetwork.model.MimeType
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toEditorBodyMode
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import kotlin.uuid.Uuid
import com.devuloopers.knet.domain.proxy.model.ProxyEngineState
import com.devuloopers.knet.domain.proxy.usecase.ObserveProxyEngineStateUseCase
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestSubTab
import com.devuloopers.knet.ui.desktop.apistudio.model.*
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseSubTab
import com.devuloopers.knet.ui.desktop.apistudio.usecase.ExecuteScriptedApiRequestUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
 * @param ioDispatcher Coroutine dispatcher for background thread dispatching.
 */
class ApiStudioViewModel(
    private val executeScriptedUseCase: ExecuteScriptedApiRequestUseCase,
    observeProxyEngineStateUseCase: ObserveProxyEngineStateUseCase,
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    private val saveWorkspaceLayoutUseCase: SaveWorkspaceLayoutUseCase,
    private val importRequestToStudioUseCase: ImportRequestToStudioUseCase,
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
                        scriptLanguage = settings.scriptLanguage
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
    }

    /**
     * Updates request headers in UDF state.
     */
    fun updateHeaders(headers: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(headers = headers)) }
    }

    /**
     * Updates request body payload string in UDF state.
     */
    fun updateBodyPayload(bodyPayload: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(bodyPayload = bodyPayload)) }
    }

    /**
     * Updates request body mode representation string in UDF state.
     */
    fun updateBodyType(bodyType: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(bodyType = bodyType)) }
    }

    /**
     * Updates cookies in UDF state.
     */
    fun updateCookies(cookies: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(cookies = cookies)) }
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
    }

    fun updateAuth(authState: AuthState) = updateAuthState(authState)

    fun updateAuth(authType: String, token: String) {
        val typeEnum = AuthType.entries.find { it.label.equals(authType, ignoreCase = true) } ?: AuthType.BEARER_TOKEN
        updateAuthState(AuthState(authType = typeEnum, bearerToken = token))
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
    }

    fun updatePreRequestScript(preRequestScript: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(preRequestScript = preRequestScript))
        }
    }

    fun updateTestScript(testScript: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(testScript = testScript))
        }
    }

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

    fun updateActiveSubTab(subTabName: String) {
        val parsed = RequestSubTab.entries.find {
            it.name.equals(subTabName, ignoreCase = true)
        } ?: RequestSubTab.BODY
        updateActiveSubTab(parsed)
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

    fun updateActiveScriptPhase(phaseName: String) {
        val parsed = ScriptPhase.entries.find {
            it.name.equals(phaseName, ignoreCase = true)
        } ?: ScriptPhase.PRE_REQUEST
        updateActiveScriptPhase(parsed)
    }

    fun updateScriptLanguage(language: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(scriptLanguage = language)) }
        viewModelScope.launch(ioDispatcher) {
            val currentSettings = getWorkspaceLayoutUseCase.execute().firstOrNull()
                ?: WorkspaceLayoutSettings()
            saveWorkspaceLayoutUseCase.execute(currentSettings.copy(scriptLanguage = language))
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

    fun updateActiveResponseSubTab(tabName: String) {
        val parsed = ResponseSubTab.entries.find {
            it.name.equals(tabName, ignoreCase = true)
        } ?: ResponseSubTab.BODY
        updateActiveResponseSubTab(parsed)
    }

    fun selectTab(tabId: String) {
        _uiState.update { it.copy(activeTabId = tabId) }
    }

    fun closeTab(tabId: String) {
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

    fun openNewTab() {
        val newId = "tab_${System.currentTimeMillis()}"
        val newTab = RequestTab(newId, "New Request")
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newId,
                editorState = RequestEditorState(
                    activeSubTab = state.editorState.activeSubTab,
                    activeScriptPhase = state.editorState.activeScriptPhase,
                    activeResponseSubTab = state.editorState.activeResponseSubTab
                )
            )
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
            val importedEditorState = RequestEditorState(
                method = normalizedSpec.methodString,
                url = normalizedSpec.url,
                headers = normalizedSpec.headers.sanitizeTransportHeaders(),
                queryParams = normalizedSpec.queryParams,
                bodyPayload = normalizedSpec.bodyPayload,
                bodyType = normalizedSpec.bodyType.toEditorBodyMode(),
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
        _uiState.update { it.copy(sessionContext = context) }
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
