package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioRequestUseCase
import com.devuloopers.knet.application.usecase.breakpoint.DropMatchingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.apistudio.usecase.DescribeRequestUseCase
import com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.usecase.GetSavedRequestUseCase
import com.devuloopers.knet.domain.collection.usecase.SaveRequestToCollectionUseCase
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.usecase.GetWorkspaceLayoutUseCase
import com.devuloopers.knet.domain.workspace.usecase.SaveWorkspaceLayoutUseCase
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.scripting.model.ScriptPhase
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CollectionSaveMode
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestDomainConverter.toDomainSavedRequest
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestDomainConverter.toEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorDefaults
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContextSerializer
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseSubTab
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import com.devuloopers.knet.ui.desktop.apistudio.usecase.AutoSaveApiSessionUseCase
import com.devuloopers.knet.ui.desktop.httppanel.model.AuthState
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlState
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.toAuthState
import com.devuloopers.knet.ui.desktop.httppanel.usecase.SyncBodyStateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * Owns the complete active API Studio document, its ordered persistence, and request execution lifecycle.
 *
 * Sidebar state is deliberately excluded: [CollectionsViewModel] only observes and mutates collection summaries,
 * while this ViewModel atomically loads one request, routes every editor mutation through one auto-save queue,
 * and changes draft/saved identity only after a successful transactional promotion.
 *
 * @param executeApiStudioRequestUseCase Executes the authored request including scripts and response formatting.
 * @param observeProxyRuntimeStateUseCase Observes the currently routable local proxy endpoint.
 * @param getWorkspaceLayoutUseCase Reads persisted API Studio workspace selection and view preferences.
 * @param saveWorkspaceLayoutUseCase Persists API Studio workspace selection and view preferences.
 * @param importRequestToStudioUseCase Normalizes requests imported from captured traffic.
 * @param describeRequestUseCase Resolves generated titles and semantic request metadata through ordered strategies.
 * @param dropMatchingBreakpointsUseCase Releases suspended traffic when execution fails or is cancelled.
 * @param syncBodyStateUseCase Synchronizes structured and text body editor representations.
 * @param autoSaveApiSessionUseCase Persists immutable active-document snapshots.
 * @param getSavedRequestUseCase Loads one request directly for startup restoration.
 * @param saveRequestToCollectionUseCase Transactionally promotes drafts into collections.
 * @param ioDispatcher Dispatcher used for persistence and network-related coordination.
 */
class ApiStudioViewModel(
    private val executeApiStudioRequestUseCase: ExecuteApiStudioRequestUseCase,
    observeProxyRuntimeStateUseCase: ObserveProxyRuntimeStateUseCase,
    private val getWorkspaceLayoutUseCase: GetWorkspaceLayoutUseCase,
    saveWorkspaceLayoutUseCase: SaveWorkspaceLayoutUseCase,
    private val importRequestToStudioUseCase: ImportRequestToStudioUseCase,
    private val describeRequestUseCase: DescribeRequestUseCase,
    private val dropMatchingBreakpointsUseCase: DropMatchingBreakpointsUseCase,
    private val syncBodyStateUseCase: SyncBodyStateUseCase,
    private val autoSaveApiSessionUseCase: AutoSaveApiSessionUseCase,
    private val getSavedRequestUseCase: GetSavedRequestUseCase,
    private val saveRequestToCollectionUseCase: SaveRequestToCollectionUseCase,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        /** Minimum loading duration preventing single-frame execution-state flashes. */
        const val MIN_LOADING_DURATION_MS: Long = 200L

        /** Quiet period preventing protocol parsing on every request editor keystroke. */
        const val REQUEST_NAME_DEBOUNCE_MS: Long = 250L
    }

    private val _uiState = MutableStateFlow(ApiStudioState())

    /** Immutable API Studio state consumed by the screen. */
    val uiState: StateFlow<ApiStudioState> = _uiState.asStateFlow()

    private var documentRevision = 0L
    private var executionRevision = 0L
    private var executionJob: Job? = null
    private var requestNameRevision = 0L
    private var requestNameJob: Job? = null

    private val autoSaveCoordinator = ApiStudioAutoSaveCoordinator(
        scope = viewModelScope,
        dispatcher = ioDispatcher,
        persist = { snapshot ->
            autoSaveApiSessionUseCase.execute(
                sessionContext = snapshot.context,
                documentTitle = snapshot.title,
                nameOrigin = snapshot.nameOrigin,
                editorState = snapshot.editorState
            )
        },
        onFailure = { failure ->
            _uiState.update {
                it.copy(persistenceErrorMessage = failure.message ?: "Request changes could not be saved.")
            }
        }
    )

    private val workspaceCoordinator = ApiStudioWorkspaceCoordinator(
        scope = viewModelScope,
        dispatcher = ioDispatcher,
        getWorkspaceLayout = getWorkspaceLayoutUseCase,
        saveWorkspaceLayout = saveWorkspaceLayoutUseCase,
        onFailure = ::publishPersistenceFailure
    )

    private val activeProxyPort: StateFlow<Int?> = observeProxyRuntimeStateUseCase.execute()
        .map { runtimeState ->
            (runtimeState as? ProxyRuntimeState.Running)
                ?.handle
                ?.endpoints
                ?.endpoints
                ?.firstOrNull()
                ?.port
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        restoreWorkspaceDocument()
    }

    /** Updates the target URL and its derived query-parameter rows. */
    fun updateUrl(url: String) = mutateEditor { editor ->
        editor.copy(
            url = url,
            queryParams = UrlQueryStringParser.parseQueryParams(url).mapIndexed { index, (name, value) ->
                KeyValueEntry("query-$index", name, value)
            }
        )
    }

    /** Updates query-parameter rows and rebuilds the target URL from the same source of truth. */
    fun updateQueryParams(queryParams: List<KeyValueEntry>) = mutateEditor { editor ->
        editor.copy(
            url = UrlQueryStringParser.rebuildUrlWithQueryParams(
                editor.url,
                queryParams.filter { it.enabled }.map { it.key to it.value }
            ),
            queryParams = queryParams
        )
    }

    /** Updates the active HTTP method. */
    fun updateMethod(method: HttpMethod) = mutateEditor { editor -> editor.copy(method = method) }

    /** Updates request headers. */
    fun updateHeaders(headers: List<KeyValueEntry>) = mutateEditor { editor ->
        editor.copy(
            headers = headers,
            automaticHeaderIds = editor.automaticHeaderIds.intersect(headers.mapTo(mutableSetOf()) { it.id })
        )
    }

    /** Updates and hydrates the complete strongly typed body state. */
    fun updateBodyState(bodyState: RequestBodyState) = mutateEditor { editor ->
        editor.copy(bodyState = syncBodyStateUseCase.ensureHydrated(bodyState))
    }

    /** Updates structured GraphQL state and its serialized request payload. */
    fun updateGraphQlState(graphQlState: GraphQlState) = mutateEditor { editor ->
        editor.copy(bodyState = syncBodyStateUseCase.updateGraphQlState(editor.bodyState, graphQlState))
    }

    /** Updates request cookies. */
    fun updateCookies(cookies: List<KeyValueEntry>) =
        mutateEditor { editor -> editor.copy(cookies = cookies) }

    /** Updates the strongly typed authentication configuration. */
    fun updateAuthState(authState: AuthState) =
        mutateEditor { editor -> editor.copy(authState = authState) }

    /** Updates the pre-request script source. */
    fun updatePreRequestScript(preRequestScript: String) =
        mutateEditor { editor -> editor.copy(preRequestScript = preRequestScript) }

    /** Updates the post-response test script source. */
    fun updateTestScript(testScript: String) =
        mutateEditor { editor -> editor.copy(testScript = testScript) }

    /** Selects the request editor sub-tab without treating view state as a document edit. */
    fun updateActiveSubTab(subTab: InspectorSubTab) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeSubTab = subTab)) }
        updateWorkspaceSettings { it.copy(activeRequestSubTab = subTab.name) }
    }

    /** Selects the active script phase without treating view state as a document edit. */
    fun updateActiveScriptPhase(phase: ScriptPhase) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeScriptPhase = phase)) }
        updateWorkspaceSettings { it.copy(activeScriptPhase = phase.name) }
    }

    /** Changes the persisted document scripting language and the default workspace preference. */
    fun updateScriptLanguage(language: ScriptLanguage) {
        mutateEditor { editor -> editor.copy(scriptLanguage = language) }
        updateWorkspaceSettings { it.copy(scriptLanguage = language.name) }
    }

    /** Selects the response inspector sub-tab. */
    fun updateActiveResponseSubTab(tab: ResponseSubTab) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeResponseSubTab = tab)) }
        updateWorkspaceSettings { it.copy(activeResponseSubTab = tab.name) }
    }

    /**
     * Atomically opens a sidebar request without emitting intermediate auto-save snapshots.
     *
     * @param item Complete sidebar projection containing the persisted domain document and location.
     */
    fun openRequest(item: SidebarRequestItem) {
        enqueueCurrentFlush()
        val context = if (item.collectionId != null && item.folderId != null) {
            SessionContext.SavedRequest(item.id, item.collectionId, item.folderId)
        } else {
            SessionContext.UnsavedDraft(item.id)
        }
        val document = item.document
        val editor = document.toEditorState(_uiState.value.editorState).hydrateStructuredBody()
        publishDocument(document.name, document.nameOrigin, context, editor)
        scheduleGeneratedRequestName(immediate = true)
        saveSessionContextToPreferences(context)
    }

    /** Creates and immediately persists a blank draft owned by this ViewModel. */
    fun createNewDraft() {
        enqueueCurrentFlush()
        val id = "draft_${Uuid.random()}"
        val context = SessionContext.UnsavedDraft(id)
        val editor = blankEditor(_uiState.value.editorState)
        publishDocument(
            title = DescribeRequestUseCase.UNTITLED_REQUEST,
            nameOrigin = RequestNameOrigin.GENERATED,
            context = context,
            editorState = editor
        )
        saveSessionContextToPreferences(context)
        flushSnapshot(snapshot())
    }

    /** Imports captured traffic as a new durable draft and returns its identifier. */
    fun importRequestSpec(spec: NetworkRequestSpec, title: String? = null): String {
        enqueueCurrentFlush()
        val imported = importRequestToStudioUseCase.execute(spec, title)
        val normalized = imported.spec
        val id = "draft_${Uuid.random()}"
        val context = SessionContext.UnsavedDraft(id)
        val authState = normalized.auth.toAuthState()
        val editor = RequestEditorState(
            method = normalized.method,
            url = normalized.url,
            headers = normalized.headers.sanitizeTransportHeaders().mapIndexed { index, (name, value) ->
                KeyValueEntry("header-$index", name, value)
            },
            queryParams = normalized.queryParams.mapIndexed { index, (name, value) ->
                KeyValueEntry("query-$index", name, value)
            },
            bodyState = RequestBodyState.from(
                PayloadInspectionSpec.fromPayload(normalized.headers, normalized.bodyPayload)
            ),
            cookies = normalized.cookies.mapIndexed { index, (name, value) ->
                KeyValueEntry("cookie-$index", name, value)
            },
            authState = authState,
            activeSubTab = _uiState.value.editorState.activeSubTab,
            activeScriptPhase = _uiState.value.editorState.activeScriptPhase,
            activeResponseSubTab = _uiState.value.editorState.activeResponseSubTab
        )
        val requestedTitle = imported.requestedTitle
        publishDocument(
            title = requestedTitle ?: DescribeRequestUseCase.UNTITLED_REQUEST,
            nameOrigin = if (requestedTitle == null) {
                RequestNameOrigin.GENERATED
            } else {
                RequestNameOrigin.USER_DEFINED
            },
            context = context,
            editorState = editor
        )
        scheduleGeneratedRequestName(immediate = true)
        saveSessionContextToPreferences(context)
        flushSnapshot(snapshot())
        return id
    }

    /** Opens the promotion dialog owned by the active-document state. */
    fun openSaveDialog() {
        ensureDraftForEdit()
        _uiState.update { it.copy(isSaveDialogOpen = true) }
    }

    /** Closes the promotion dialog without changing the active document. */
    fun closeSaveDialog() {
        _uiState.update { it.copy(isSaveDialogOpen = false) }
    }

    /**
     * Promotes the current draft and changes its session identity only after persistence succeeds.
     *
     * @param requestName User-visible saved request title.
     * @param mode Existing or new collection destination mode.
     * @param selectedFolder Typed existing folder destination, when applicable.
     * @param newCollectionName New collection title, when applicable.
     */
    fun saveRequestToCollection(
        requestName: String,
        mode: CollectionSaveMode,
        selectedFolder: SidebarFolderItem?,
        newCollectionName: String
    ) {
        ensureDraftForEdit()
        val sourceState = _uiState.value
        val sourceContext = sourceState.sessionContext
        val submittedName = requestName.trim()
        val submittedNameOrigin = sourceState.submittedNameOrigin(submittedName)
        if (submittedNameOrigin == RequestNameOrigin.USER_DEFINED) {
            requestNameRevision++
            requestNameJob?.cancel()
        }
        if (sourceContext is SessionContext.SavedRequest) {
            _uiState.update {
                it.copy(
                    activeDocumentTitle = submittedName,
                    activeDocumentNameOrigin = submittedNameOrigin,
                    isSaveDialogOpen = false
                )
            }
            flushSnapshot(snapshot())
            return
        }
        val draftContext = sourceContext as? SessionContext.UnsavedDraft ?: return
        val draftSnapshot = snapshot(sourceState)
        viewModelScope.launch(ioDispatcher) {
            try {
                autoSaveCoordinator.flush(draftSnapshot).getOrThrow()

                val savedRequestId = "req_${Uuid.random()}"
                val savedRequest = sourceState.editorState.toDomainSavedRequest(
                    id = savedRequestId,
                    name = submittedName,
                    nameOrigin = submittedNameOrigin
                )
                val destination = when (mode) {
                    CollectionSaveMode.EXISTING_COLLECTION -> {
                        val folder = requireNotNull(selectedFolder) {
                            "Choose a collection before saving the request."
                        }
                        saveRequestToCollectionUseCase.executeExisting(
                            collectionId = folder.collectionId,
                            folderId = folder.id,
                            request = savedRequest,
                            unsavedRequestIdToDelete = draftContext.sessionId
                        )
                        folder.collectionId to folder.id
                    }
                    CollectionSaveMode.NEW_COLLECTION -> {
                        val collectionId = "col_${Uuid.random()}"
                        val folderId = "fld_${Uuid.random()}"
                        saveRequestToCollectionUseCase.executeNew(
                            collection = ApiCollection(collectionId, newCollectionName.trim()),
                            folder = CollectionFolder(folderId, "Requests"),
                            request = savedRequest,
                            unsavedRequestIdToDelete = draftContext.sessionId
                        )
                        collectionId to folderId
                    }
                }
                autoSaveCoordinator.discard(draftSnapshot.key)
                val savedContext = SessionContext.SavedRequest(savedRequestId, destination.first, destination.second)
                _uiState.update { state ->
                    state.copy(
                        editorState = state.editorState,
                        sessionContext = savedContext,
                        activeDocumentTitle = savedRequest.name,
                        activeDocumentNameOrigin = savedRequest.nameOrigin,
                        selectedRequestId = savedRequestId,
                        isSaveDialogOpen = false,
                        persistenceErrorMessage = null
                    )
                }
                saveSessionContext(savedContext)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                publishPersistenceFailure(failure)
            }
        }
    }

    /** Updates the active document title after a successful sidebar rename. */
    fun renameActiveDocument(requestId: String, newName: String) {
        if (_uiState.value.selectedRequestId != requestId) return
        requestNameRevision++
        requestNameJob?.cancel()
        _uiState.update { state ->
            state.copy(
                activeDocumentTitle = newName.trim(),
                activeDocumentNameOrigin = RequestNameOrigin.USER_DEFINED
            )
        }
    }

    /** Clears a request removed from persistence without allowing a pending save to recreate it. */
    fun closeTab(tabId: String) {
        val state = _uiState.value
        if (state.selectedRequestId != tabId) return
        val key = snapshot(state).key
        viewModelScope.launch(ioDispatcher) { autoSaveCoordinator.discard(key) }
        clearSessionContext(flushCurrent = false)
    }

    /** Clears the current editor after first flushing its latest authored state. */
    fun clearSessionContext() = clearSessionContext(flushCurrent = true)

    /** Clears the response inspector while retaining the authored request. */
    fun clearResponse() {
        _uiState.update {
            it.copy(executionState = ExecutionState.IDLE, responseInspection = null, errorMessage = null)
        }
    }

    /** Executes the latest immutable editor snapshot with stale-result and cancellation protection. */
    fun executeRequest() {
        ensureDraftForEdit()
        val currentEditor = _uiState.value.editorState
        val executionDocument = currentEditor.toDomainSavedRequest(
            id = _uiState.value.selectedRequestId ?: "execution",
            name = _uiState.value.activeDocumentTitle,
            nameOrigin = _uiState.value.activeDocumentNameOrigin
        )
        flushSnapshot(snapshot())
        val requestExecutionRevision = ++executionRevision
        executionJob?.cancel()
        _uiState.update { it.copy(executionState = ExecutionState.EXECUTING, errorMessage = null) }
        val executionTimer = TimeSource.Monotonic.markNow()

        executionJob = viewModelScope.launch {
            try {
                val result = executeApiStudioRequestUseCase.execute(executionDocument, activeProxyPort.value)
                coroutineContext.ensureActive()
                if (requestExecutionRevision != executionRevision) return@launch

                if (!result.result.isSuccess) {
                    dropMatchingBreakpointsSafely(currentEditor)
                }
                awaitMinimumLoadingDuration(executionTimer)
                coroutineContext.ensureActive()
                if (requestExecutionRevision != executionRevision) return@launch

                _uiState.update {
                    it.copy(
                        executionState = if (result.result.isSuccess) ExecutionState.SUCCESS else ExecutionState.ERROR,
                        responseInspection = ResponseInspectorState(
                            statusCode = result.result.statusCode,
                            statusText = result.result.statusText,
                            durationMs = result.result.latencyMs,
                            sizeBytes = result.result.responseSizeBytes,
                            headers = result.result.headers,
                            cookies = result.result.cookies,
                            responseBody = result.formattedBody,
                            testResults = result.testResults,
                            consoleLogs = result.consoleLogs,
                            failureReason = result.result.failureReason,
                            errorMessage = result.result.errorMessage,
                            executionState = if (result.result.isSuccess) ExecutionState.SUCCESS else ExecutionState.ERROR
                        ),
                        errorMessage = result.result.errorMessage
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                dropMatchingBreakpointsSafely(currentEditor)
                awaitMinimumLoadingDuration(executionTimer)
                if (requestExecutionRevision == executionRevision) {
                    _uiState.update {
                        it.copy(
                            executionState = ExecutionState.ERROR,
                            errorMessage = failure.message ?: "Failed to execute request"
                        )
                    }
                }
            } finally {
                if (requestExecutionRevision == executionRevision) {
                    executionJob = null
                }
            }
        }
    }

    /** Cancels only the currently active execution and prevents it from publishing a stale result. */
    fun cancelExecution() {
        val currentEditor = _uiState.value.editorState
        executionRevision++
        executionJob?.cancel()
        executionJob = null
        _uiState.update { it.copy(executionState = ExecutionState.IDLE, errorMessage = null) }
        viewModelScope.launch(ioDispatcher) { dropMatchingBreakpointsSafely(currentEditor) }
    }

    private fun mutateEditor(transform: (RequestEditorState) -> RequestEditorState) {
        ensureDraftForEdit()
        _uiState.update { state ->
            val editor = transform(state.editorState)
            state.copy(
                editorState = editor,
                persistenceErrorMessage = null
            )
        }
        scheduleGeneratedRequestName()
        autoSaveCoordinator.schedule(snapshot())
    }

    private fun ensureDraftForEdit() {
        if (_uiState.value.sessionContext !is SessionContext.None) return
        val id = "draft_${Uuid.random()}"
        val context = SessionContext.UnsavedDraft(id)
        _uiState.update { state ->
            state.copy(
                editorState = state.editorState,
                sessionContext = context,
                activeDocumentTitle = DescribeRequestUseCase.UNTITLED_REQUEST,
                activeDocumentNameOrigin = RequestNameOrigin.GENERATED,
                selectedRequestId = id,
                isRestoring = false
            )
        }
        saveSessionContextToPreferences(context)
    }

    private fun publishDocument(
        title: String,
        nameOrigin: RequestNameOrigin,
        context: SessionContext,
        editorState: RequestEditorState
    ) {
        requestNameRevision++
        requestNameJob?.cancel()
        val documentId = when (context) {
            is SessionContext.UnsavedDraft -> context.sessionId
            is SessionContext.SavedRequest -> context.requestId
            SessionContext.None -> ""
        }
        _uiState.update { state ->
            state.copy(
                editorState = editorState,
                responseInspection = null,
                executionState = ExecutionState.IDLE,
                errorMessage = null,
                sessionContext = context,
                activeDocumentTitle = title,
                activeDocumentNameOrigin = nameOrigin,
                selectedRequestId = documentId.ifBlank { null },
                isRestoring = false,
                persistenceErrorMessage = null
            )
        }
    }

    private fun scheduleGeneratedRequestName(immediate: Boolean = false) {
        val sourceState = _uiState.value
        if (sourceState.activeDocumentNameOrigin != RequestNameOrigin.GENERATED) return
        if (sourceState.sessionContext is SessionContext.None) return

        val sourceContext = sourceState.sessionContext
        val sourceRequestId = sourceState.selectedRequestId ?: return
        val sourceRevision = ++requestNameRevision
        val sourceEditor = sourceState.editorState
        val sourceTitle = sourceState.activeDocumentTitle

        requestNameJob?.cancel()
        requestNameJob = viewModelScope.launch {
            if (!immediate) delay(REQUEST_NAME_DEBOUNCE_MS.milliseconds)
            val suggestion = withContext(ioDispatcher) {
                describeRequestUseCase.execute(
                    sourceEditor.toDomainSavedRequest(
                        id = sourceRequestId,
                        name = sourceTitle,
                        nameOrigin = RequestNameOrigin.GENERATED
                    )
                ).suggestedName
            }
            if (sourceRevision != requestNameRevision) return@launch

            val currentState = _uiState.value
            if (currentState.sessionContext != sourceContext) return@launch
            if (currentState.activeDocumentNameOrigin != RequestNameOrigin.GENERATED) return@launch
            if (currentState.activeDocumentTitle == suggestion) return@launch

            _uiState.update { state -> state.copy(activeDocumentTitle = suggestion) }
            autoSaveCoordinator.schedule(snapshot())
            requestNameJob = null
        }
    }

    private fun ApiStudioState.submittedNameOrigin(submittedName: String): RequestNameOrigin =
        if (
            activeDocumentNameOrigin == RequestNameOrigin.GENERATED &&
            activeDocumentTitle.trim() == submittedName
        ) {
            RequestNameOrigin.GENERATED
        } else {
            RequestNameOrigin.USER_DEFINED
        }

    private fun clearSessionContext(flushCurrent: Boolean) {
        if (flushCurrent) enqueueCurrentFlush()
        val currentEditor = _uiState.value.editorState
        publishDocument(
            title = "New Request",
            nameOrigin = RequestNameOrigin.GENERATED,
            context = SessionContext.None,
            editorState = blankEditor(currentEditor)
        )
        saveSessionContextToPreferences(SessionContext.None)
    }

    private fun blankEditor(previous: RequestEditorState): RequestEditorState = RequestEditorState(
        headers = RequestEditorDefaults.DEFAULT_HEADERS,
        activeSubTab = previous.activeSubTab,
        activeScriptPhase = previous.activeScriptPhase,
        activeResponseSubTab = previous.activeResponseSubTab,
        scriptLanguage = previous.scriptLanguage
    )

    private fun snapshot(state: ApiStudioState = _uiState.value): ApiStudioAutoSaveSnapshot =
        ApiStudioAutoSaveSnapshot(
            context = state.sessionContext,
            title = state.activeDocumentTitle,
            nameOrigin = state.activeDocumentNameOrigin,
            editorState = state.editorState,
            revision = ++documentRevision
        )

    private fun enqueueCurrentFlush() {
        val state = _uiState.value
        if (state.sessionContext is SessionContext.None) return
        flushSnapshot(snapshot(state))
    }

    private fun flushSnapshot(snapshot: ApiStudioAutoSaveSnapshot) {
        viewModelScope.launch(ioDispatcher) {
            autoSaveCoordinator.flush(snapshot).onFailure(::publishPersistenceFailure)
        }
    }

    private fun restoreWorkspaceDocument() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val settings = getWorkspaceLayoutUseCase.execute().first()
                val requestSubTab = InspectorSubTab.entries.firstOrNull {
                    it.name.equals(settings.activeRequestSubTab, ignoreCase = true)
                } ?: InspectorSubTab.BODY
                val scriptPhase = ScriptPhase.entries.firstOrNull {
                    it.name.equals(settings.activeScriptPhase, ignoreCase = true)
                } ?: ScriptPhase.PRE_REQUEST
                val responseSubTab = ResponseSubTab.entries.firstOrNull {
                    it.name.equals(settings.activeResponseSubTab, ignoreCase = true)
                } ?: ResponseSubTab.BODY
                val defaultScriptLanguage = ScriptLanguage.entries.firstOrNull {
                    it.name.equals(settings.scriptLanguage, ignoreCase = true)
                } ?: ScriptLanguage.JAVASCRIPT
                val context = SessionContextSerializer.deserialize(settings.activeSessionId)
                val requestId = when (context) {
                    is SessionContext.UnsavedDraft -> context.sessionId
                    is SessionContext.SavedRequest -> context.requestId
                    SessionContext.None -> null
                }
                val request = requestId?.let { getSavedRequestUseCase.execute(it) }
                if (!_uiState.value.isRestoring) return@launch
                if (request != null) {
                    val previous = _uiState.value.editorState.copy(
                        activeSubTab = requestSubTab,
                        activeScriptPhase = scriptPhase,
                        activeResponseSubTab = responseSubTab
                    )
                    publishDocument(
                        title = request.name,
                        nameOrigin = request.nameOrigin,
                        context = context,
                        editorState = request.toEditorState(previous).hydrateStructuredBody()
                    )
                    scheduleGeneratedRequestName(immediate = true)
                } else {
                    _uiState.update { state ->
                        state.copy(
                            editorState = state.editorState.copy(
                                activeSubTab = requestSubTab,
                                activeScriptPhase = scriptPhase,
                                activeResponseSubTab = responseSubTab,
                                scriptLanguage = defaultScriptLanguage
                            ),
                            sessionContext = SessionContext.None,
                            selectedRequestId = null,
                            isRestoring = false
                        )
                    }
                    if (context !is SessionContext.None) saveSessionContext(SessionContext.None)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update { state ->
                    if (!state.isRestoring) state else state.copy(
                        isRestoring = false,
                        persistenceErrorMessage = failure.message ?: "API Studio workspace could not be restored."
                    )
                }
            }
        }
    }

    private fun saveSessionContextToPreferences(context: SessionContext) {
        workspaceCoordinator.schedule { settings ->
            settings.copy(activeSessionId = SessionContextSerializer.serialize(context))
        }
    }

    private suspend fun saveSessionContext(context: SessionContext) {
        workspaceCoordinator.updateAndAwait { settings ->
            settings.copy(activeSessionId = SessionContextSerializer.serialize(context))
        }.getOrThrow()
    }

    private fun updateWorkspaceSettings(transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings) {
        workspaceCoordinator.schedule(transform)
    }

    private suspend fun dropMatchingBreakpointsSafely(editor: RequestEditorState) {
        try {
            dropMatchingBreakpointsUseCase.execute(editor.url, editor.method.token)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Breakpoint cleanup is best-effort and must not replace the primary execution result.
        }
    }

    private suspend fun awaitMinimumLoadingDuration(startedAt: TimeMark) {
        val elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds
        if (elapsedMillis < MIN_LOADING_DURATION_MS) {
            delay((MIN_LOADING_DURATION_MS - elapsedMillis).milliseconds)
        }
    }

    private fun RequestEditorState.hydrateStructuredBody(): RequestEditorState =
        copy(bodyState = syncBodyStateUseCase.ensureHydrated(bodyState))

    private fun publishPersistenceFailure(failure: Throwable?) {
        _uiState.update {
            it.copy(persistenceErrorMessage = failure?.message ?: "Request changes could not be saved.")
        }
    }
}
