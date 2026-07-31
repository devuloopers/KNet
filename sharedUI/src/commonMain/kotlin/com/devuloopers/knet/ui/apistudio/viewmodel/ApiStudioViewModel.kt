package com.devuloopers.knet.ui.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.apistudio.detector.UrlParameterExtractor
import com.devuloopers.knet.domain.apistudio.exporter.PostmanCollectionExporter
import com.devuloopers.knet.domain.apistudio.importer.PostmanCollectionImporter
import com.devuloopers.knet.domain.apistudio.model.*
import com.devuloopers.knet.domain.apistudio.repository.CollectionsRepository
import com.devuloopers.knet.domain.apistudio.runner.CollectionTestRunner
import com.devuloopers.knet.domain.apistudio.runner.SuiteRequestResult
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary
import com.devuloopers.knet.engine.client.KNetApiClient
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.ui.apistudio.handler.CollectionHandler
import com.devuloopers.knet.ui.apistudio.handler.ExecutionHandler
import com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing state and API request execution for the API Studio Screen.
 *
 * @param repository Optional repository for persisting collections.
 * @param proxyPort Optional proxy port for HTTP proxy interception.
 */
class ApiStudioViewModel(
    private val repository: CollectionsRepository? = null,
    proxyPort: Int? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiStudioUiState())
    val uiState: StateFlow<ApiStudioUiState> = _uiState.asStateFlow()

    private val apiClient = KNetApiClient(proxyPort)
    private val testRunner = CollectionTestRunner()
    private val urlParameterExtractor = UrlParameterExtractor()
    private val collectionHandler = CollectionHandler(repository)
    private val executionHandler = ExecutionHandler(proxyPort, apiClient, testRunner)

    init {

        if (repository != null) {
            viewModelScope.launch {
                repository.observeCollections().collect { savedCollections ->
                    _uiState.update { state ->
                        state.copy(collections = savedCollections)
                    }
                }
            }
            viewModelScope.launch {
                repository.observeUnsavedRequests().collect { persistedUnsaved ->
                    _uiState.update { state ->
                        val selected = state.selectedRequest ?: persistedUnsaved.firstOrNull()
                        state.copy(
                            unsavedRequests = persistedUnsaved,
                            selectedRequest = selected
                        )
                    }
                }
            }
        }
    }

    /**
     * Helper method to sync all modifications to the active request into the unsavedRequests
     * list (or collections if saved) in real-time and persist to Room DB.
     */
    private fun syncSelectedRequestInList(updatedReq: SavedApiRequest) {
        val isUnsaved = _uiState.value.unsavedRequests.any { it.id == updatedReq.id }
        _uiState.update { state ->
            val updatedUnsaved = if (isUnsaved) {
                state.unsavedRequests.map { if (it.id == updatedReq.id) updatedReq else it }
            } else state.unsavedRequests

            val updatedCollections = state.collections.map { col ->
                col.copy(folders = col.folders.map { folder ->
                    folder.copy(requests = folder.requests.map { req ->
                        if (req.id == updatedReq.id) updatedReq else req
                    })
                })
            }

            state.copy(
                selectedRequest = updatedReq,
                unsavedRequests = updatedUnsaved,
                collections = updatedCollections
            )
        }
        viewModelScope.launch {
            if (isUnsaved) {
                repository?.saveUnsavedRequest(updatedReq)
            } else {
                val targetCollection = _uiState.value.collections.find { col ->
                    col.folders.any { folder -> folder.requests.any { it.id == updatedReq.id } }
                }
                val targetFolder = targetCollection?.folders?.find { folder ->
                    folder.requests.any { it.id == updatedReq.id }
                }
                if (targetCollection != null && targetFolder != null) {
                    repository?.saveRequest(targetCollection.id, targetFolder.id, updatedReq)
                }
            }
        }
    }


    fun clearResponseState() {
        _uiState.update {
            it.copy(
                latestResult = null,
                testResults = emptyList(),
                scriptErrorMessage = null
            )
        }
    }

    /**
     * Called on every URL field keypress. Extracts path variables and query params.
     * Auto-creates an Unsaved Request session on the very first typed character.
     */
    fun onUrlInputChanged(newUrl: String) {
        val parseResult = urlParameterExtractor.extract(newUrl)
        val currentReq = _uiState.value.selectedRequest

        if (currentReq == null && newUrl.isNotBlank()) {
            createUnsavedRequest(
                initialRequest = _uiState.value.draftRequest.copy(url = newUrl)
            )
        } else {
            val target = currentReq ?: _uiState.value.draftRequest
            val updated = target.copy(url = newUrl)
            if (currentReq != null) {
                syncSelectedRequestInList(updated)
            } else {
                _uiState.update { it.copy(draftRequest = updated) }
            }
        }
        _uiState.update { it.copy(detectedPathParams = parseResult.pathVariables) }
    }

    /**
     * Updates the HTTP method (GET, POST, PUT, DELETE, etc.) for the selected or draft request.
     */
    fun updateMethod(newMethod: HttpMethod) {
        clearResponseState()
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(method = newMethod)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }


    /**
     * Toggles the enabled/disabled state of a header row by key.
     */
    fun toggleHeader(key: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updatedHeaders = target.headers.map { header ->
            if (header.key == key) header.copy(isEnabled = !header.isEnabled) else header
        }
        val updated = target.copy(headers = updatedHeaders)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }

    /**
     * Updates the value of a header row identified by key.
     */
    fun updateHeaderValue(key: String, newValue: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updatedHeaders = target.headers.map { header ->
            if (header.key == key) header.copy(value = newValue) else header
        }
        val updated = target.copy(headers = updatedHeaders)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }

    /**
     * Adds a new user-defined header row.
     */
    fun addHeader(key: String = "", value: String = "") {
        val newHeader = RequestHeader(key = key, value = value, isEnabled = true, isAuto = false)
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(headers = target.headers + newHeader)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }

    /**
     * Removes a header row by key.
     */
    fun removeHeader(key: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updatedHeaders = target.headers.filter { it.key != key }
        val updated = target.copy(headers = updatedHeaders)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }

    /**
     * Updates the key of a header row.
     */
    fun updateHeaderKey(oldKey: String, newKey: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updatedHeaders = target.headers.map { header ->
            if (header.key == oldKey) header.copy(key = newKey, isAuto = false) else header
        }
        val updated = target.copy(headers = updatedHeaders)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }

    /**
     * Updates the request payload body.
     */
    fun updateRequestBody(newBody: String) = updateActiveRequest { req ->
        req.copy(body = req.body.copy(content = newBody))
    }

    /**
     * Updates the request body mode (none, json, form-data, x-www-form-urlencoded, raw, graphql)
     * and auto-syncs the Content-Type header accordingly.
     */
    fun updateRequestBodyType(newType: String) {
        val selectedReq = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val contentTypeHeader = when (newType.lowercase()) {
            "json", "graphql" -> "application/json"
            "form-data" -> "multipart/form-data"
            "x-www-form-urlencoded" -> "application/x-www-form-urlencoded"
            "raw-xml" -> "application/xml"
            "raw-html" -> "text/html"
            "raw-text" -> "text/plain"
            else -> null
        }

        val currentHeaders = selectedReq.headers.toMutableList()
        val existingIndex = currentHeaders.indexOfFirst { it.key.equals("Content-Type", ignoreCase = true) }

        if (contentTypeHeader != null) {
            if (existingIndex >= 0) {
                currentHeaders[existingIndex] =
                    currentHeaders[existingIndex].copy(value = contentTypeHeader, isEnabled = true)
            } else {
                currentHeaders.add(
                    RequestHeader(
                        key = "Content-Type",
                        value = contentTypeHeader,
                        isEnabled = true,
                        isAuto = false
                    )
                )
            }
        } else if (newType.lowercase() == "none" && existingIndex >= 0) {
            currentHeaders[existingIndex] = currentHeaders[existingIndex].copy(isEnabled = false)
        }

        val updated = selectedReq.copy(body = selectedReq.body.copy(type = newType), headers = currentHeaders)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }

    /**
     * Restores default headers.
     */
    fun restoreDefaultHeaders() {
        val selectedReq = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val existingKeys = selectedReq.headers.map { it.key }.toSet()
        val missingDefaults = defaultHeaders().filter { it.key !in existingKeys }
        val updated = selectedReq.copy(headers = missingDefaults + selectedReq.headers)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }


    fun updateAuthType(type: String) = updateActiveRequest { target ->
        val currentAuth = target.auth
        target.copy(
            auth = when (type) {
                "Bearer Token" -> ApiRequestAuth.Bearer(if (currentAuth is ApiRequestAuth.Bearer) currentAuth.token else "")
                "Basic Auth" -> ApiRequestAuth.Basic(
                    username = if (currentAuth is ApiRequestAuth.Basic) currentAuth.username else "",
                    password = if (currentAuth is ApiRequestAuth.Basic) currentAuth.password else ""
                )

                "API Key" -> ApiRequestAuth.ApiKey(
                    name = if (currentAuth is ApiRequestAuth.ApiKey) currentAuth.name else "X-API-Key",
                    value = if (currentAuth is ApiRequestAuth.ApiKey) currentAuth.value else "",
                    location = if (currentAuth is ApiRequestAuth.ApiKey) currentAuth.location else "Header"
                )

                "OAuth 2.0" -> ApiRequestAuth.OAuth2(
                    token = if (currentAuth is ApiRequestAuth.OAuth2) currentAuth.token else "",
                    headerPrefix = if (currentAuth is ApiRequestAuth.OAuth2) currentAuth.headerPrefix else "Bearer"
                )

                "AWS Signature" -> ApiRequestAuth.AwsSignature(
                    accessKey = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.accessKey else "",
                    secretKey = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.secretKey else "",
                    region = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.region else "us-east-1",
                    service = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.service else "execute-api"
                )

                "Inherit Auth" -> ApiRequestAuth.Inherit
                else -> ApiRequestAuth.None
            }
        )
    }

    fun updateAuthToken(token: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.Bearer -> a.copy(token = token)
                is ApiRequestAuth.OAuth2 -> a.copy(token = token)
                else -> ApiRequestAuth.Bearer(token)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update {
            it.copy(
                draftRequest = updated
            )
        }
    }

    fun updateAuthUsername(username: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.Basic -> a.copy(username = username)
                else -> ApiRequestAuth.Basic(username, "")
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update {
            it.copy(
                draftRequest = updated
            )
        }
    }

    fun updateAuthPassword(password: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.Basic -> a.copy(password = password)
                else -> ApiRequestAuth.Basic("", password)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update {
            it.copy(
                draftRequest = updated
            )
        }
    }

    private val scriptAnalyzer = com.devuloopers.knet.ui.apistudio.scriptanalyzer.ScriptAnalyzer()

    fun updatePreRequestScript(script: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(scripts = target.scripts.copy(preRequest = script))
        val analysisRes = scriptAnalyzer.analyze(
            code = script,
            phase = com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptExecutionPhase.PRE_REQUEST
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update {
            it.copy(
                draftRequest = updated
            )
        }
        _uiState.update { it.copy(analysisResult = analysisRes, isExecutionStale = _uiState.value.latestResult != null) }
    }

    fun updateTestScript(script: String) {
        updateActiveRequest { req ->
            req.copy(scripts = req.scripts.copy(test = script))
        }
        _uiState.update { it.copy(isExecutionStale = _uiState.value.latestResult != null) }
    }

    /**
     * Applies an IDE 1-click Quick Fix refactoring action (e.g. moving pm.test code to Tests tab).
     */
    fun applyQuickFix(quickFix: com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix) {
        when (quickFix) {
            is com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix.MoveToTestsTab -> {
                val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
                val existingTests = target.scripts.test
                val newTests =
                    if (existingTests.isNotBlank()) "$existingTests\n\n${quickFix.codeToMove}" else quickFix.codeToMove
                val updated = target.copy(
                    scripts = target.scripts.copy(
                        preRequest = "",
                        test = newTests
                    )
                )
                if (_uiState.value.selectedRequest != null) {
                    syncSelectedRequestInList(updated)
                } else {
                    _uiState.update { it.copy(draftRequest = updated) }
                }
                _uiState.update {
                    it.copy(
                        analysisResult = null,
                        activeReqTab = "Tests"
                    )
                }
            }
        }
    }

    init {
        // Observe collections from repository if provided
        if (repository != null) {
            viewModelScope.launch {
                repository.observeCollections().collect { loadedCollections ->
                    _uiState.update { state ->
                        val firstReq = loadedCollections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull()
                        state.copy(
                            collections = loadedCollections,
                            selectedRequest = state.selectedRequest ?: firstReq
                        )
                    }
                }
            }
        }
    }


    /**
     * Deletes an entire collection by ID.
     */
    fun deleteCollection(collectionId: String) {
        val updatedCollections = _uiState.value.collections.filter { it.id != collectionId }
        val newSelected = if (_uiState.value.selectedRequest?.id != null) {
            val isDeleted = _uiState.value.collections
                .find { it.id == collectionId }
                .orEmptyRequests()
                .any { it.id == _uiState.value.selectedRequest?.id }
            if (isDeleted) updatedCollections.flatMap { it.folders }.flatMap { it.requests }
                .firstOrNull() else _uiState.value.selectedRequest
        } else {
            updatedCollections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull()
        }
        _uiState.update { it.copy(collections = updatedCollections, selectedRequest = newSelected) }
        repository?.let { repo ->
            viewModelScope.launch { repo.deleteCollection(collectionId) }
        }
    }

    /**
     * Deletes a specific request by ID from a collection folder.
     */
    fun deleteRequest(collectionId: String, requestId: String) {
        val updatedCollections = _uiState.value.collections.map { col ->
            if (col.id == collectionId) {
                col.copy(folders = col.folders.map { folder ->
                    folder.copy(requests = folder.requests.filter { it.id != requestId })
                })
            } else col
        }
        val newSelected = if (_uiState.value.selectedRequest?.id == requestId) {
            updatedCollections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull()
        } else {
            _uiState.value.selectedRequest
        }
        _uiState.update { it.copy(collections = updatedCollections, selectedRequest = newSelected) }
        repository?.let { repo ->
            viewModelScope.launch { repo.deleteRequest(requestId) }
        }
    }

    private fun ApiCollection?.orEmptyRequests(): List<SavedApiRequest> {
        return this?.folders?.flatMap { it.requests } ?: emptyList()
    }

    fun selectRequest(request: SavedApiRequest) {
        clearResponseState()
        _uiState.update { it.copy(selectedRequest = request) }
    }


    fun selectReqTab(tab: String) {
        _uiState.update { it.copy(activeReqTab = tab) }
    }

    fun selectRespTab(tab: String) {
        _uiState.update { it.copy(activeRespTab = tab) }
    }

    /**
     * Creates a new unsaved ad-hoc API request session under UNSAVED SESSIONS.
     * Computes the lowest available integer N so that "Unsaved Request N" is unique in unsavedRequests.
     */
    fun createUnsavedRequest(initialRequest: SavedApiRequest? = null): SavedApiRequest {
        val activeNames = _uiState.value.unsavedRequests.map { it.name }.toSet()
        var nextNum = 1
        while (activeNames.contains("Unsaved Request $nextNum")) {
            nextNum++
        }
        val source = initialRequest ?: _uiState.value.draftRequest
        val newReq = SavedApiRequest(
            id = "unsaved-$nextNum-${kotlin.uuid.Uuid.random()}",
            name = "Unsaved Request $nextNum",
            method = source.method,
            url = source.url,
            headers = source.headers.ifEmpty { defaultHeaders() },
            body = source.body,
            auth = source.auth,
            scripts = source.scripts
        )
        _uiState.update { state ->
            state.copy(
                unsavedRequests = state.unsavedRequests + newReq,
                selectedRequest = newReq,
                draftRequest = SavedApiRequest(
                    id = "draft",
                    name = "New Request",
                    method = HttpMethod.GET,
                    url = "",
                    headers = defaultHeaders()
                )
            )
        }
        viewModelScope.launch {
            repository?.saveUnsavedRequest(newReq)
        }
        return newReq
    }

    /**
     * Renames an existing saved collection in state and updates Room DB.
     */
    fun renameCollection(collectionId: String, newName: String) {
        val col = _uiState.value.collections.find { it.id == collectionId } ?: return
        val updatedCol = col.copy(name = newName)
        _uiState.update { state ->
            val updatedCollections = state.collections.map {
                if (it.id == collectionId) updatedCol else it
            }
            state.copy(collections = updatedCollections)
        }
        viewModelScope.launch {
            repository?.saveCollection(updatedCol)
        }
    }

    /**
     * Renames an existing saved request in state and updates Room DB.
     */
    fun renameSavedRequest(requestId: String, newName: String) {
        var targetColId: String? = null
        var targetFolderId: String? = null
        var updatedRequest: SavedApiRequest? = null

        val updatedCollections = _uiState.value.collections.map { col ->
            val updatedFolders = col.folders.map { folder ->
                val updatedReqs = folder.requests.map { req ->
                    if (req.id == requestId) {
                        targetColId = col.id
                        targetFolderId = folder.id
                        val r = req.copy(name = newName)
                        updatedRequest = r
                        r
                    } else req
                }
                folder.copy(requests = updatedReqs)
            }
            col.copy(folders = updatedFolders)
        }

        if (updatedRequest != null && targetColId != null && targetFolderId != null) {
            val isSelected = _uiState.value.selectedRequest?.id == requestId
            _uiState.update { state ->
                state.copy(
                    collections = updatedCollections,
                    selectedRequest = if (isSelected) updatedRequest else state.selectedRequest
                )
            }
            viewModelScope.launch {
                repository?.saveRequest(targetColId, targetFolderId, updatedRequest)
            }

        }
    }


    /**
     * Clears current request selection to start a fresh ad-hoc draft request tab.
     */
    @Suppress("unused")
    fun startNewDraftRequest() {
        _uiState.update { state ->
            state.copy(
                selectedRequest = null,
                draftRequest = SavedApiRequest(
                    id = "draft",
                    name = "New Request",
                    method = HttpMethod.GET,
                    url = "",
                    headers = defaultHeaders()
                )
            )
        }
    }


    /**
     * Deletes an unsaved request session tab.
     */
    fun deleteUnsavedRequest(requestId: String) {
        _uiState.update { state ->
            val updatedUnsaved = state.unsavedRequests.filter { it.id != requestId }
            val newSelected = if (state.selectedRequest?.id == requestId) {
                updatedUnsaved.lastOrNull() ?: state.collections.flatMap { it.folders }.flatMap { it.requests }
                    .firstOrNull()
            } else {
                state.selectedRequest
            }
            state.copy(
                unsavedRequests = updatedUnsaved,
                selectedRequest = newSelected
            )
        }
        viewModelScope.launch {
            repository?.deleteUnsavedRequest(requestId)
        }
    }

    /**
     * Promotes an unsaved request session into a permanent saved collection.
     */
    fun saveUnsavedToCollection(
        requestId: String,
        targetCollectionId: String,
        targetFolderId: String? = null,
        customName: String? = null
    ) {
        val unsavedReq =
            _uiState.value.unsavedRequests.find { it.id == requestId } ?: _uiState.value.selectedRequest ?: return
        val promotedReq = unsavedReq.copy(
            id = "req-${kotlin.uuid.Uuid.random()}",
            name = customName?.takeIf { it.isNotBlank() } ?: unsavedReq.name
        )

        val updatedCollections = _uiState.value.collections.map { col ->
            if (col.id == targetCollectionId) {
                val effectiveFolders = col.folders.ifEmpty {
                    listOf(
                        CollectionFolder(
                            id = "folder-${kotlin.uuid.Uuid.random()}",
                            name = "General",
                            requests = emptyList()
                        )
                    )
                }
                val targetFolder = targetFolderId ?: effectiveFolders.first().id
                col.copy(folders = effectiveFolders.map { folder ->
                    if (folder.id == targetFolder) {
                        folder.copy(requests = folder.requests + promotedReq)
                    } else folder
                })
            } else col
        }

        _uiState.update { state ->
            state.copy(
                unsavedRequests = state.unsavedRequests.filter { it.id != requestId },
                collections = updatedCollections,
                selectedRequest = promotedReq
            )
        }


        viewModelScope.launch {
            repository?.deleteUnsavedRequest(requestId)
            val folderId = targetFolderId
                ?: _uiState.value.collections.find { it.id == targetCollectionId }?.folders?.firstOrNull()?.id ?: ""
            repository?.saveRequest(targetCollectionId, folderId, promotedReq)
        }
    }

    /**
     * Creates a new collection and immediately saves an unsaved request session into it.
     */
    fun saveUnsavedToNewCollection(
        requestId: String,
        collectionName: String,
        requestName: String? = null
    ) {
        val (updatedCols, updatedUnsaved, promotedReq) = collectionHandler.saveUnsavedToNewCollection(
            collections = _uiState.value.collections,
            unsavedRequests = _uiState.value.unsavedRequests,
            selectedRequest = _uiState.value.selectedRequest,
            requestId = requestId,
            collectionName = collectionName,
            requestName = requestName
        )
        if (promotedReq == null) return

        val newCol = updatedCols.last()
        val defaultFolder = newCol.folders.first()

        _uiState.update { state ->
            state.copy(
                collections = updatedCols,
                unsavedRequests = updatedUnsaved,
                selectedRequest = promotedReq
            )
        }

        viewModelScope.launch {
            repository?.saveUnsavedToNewCollectionTx(
                collection = newCol,
                folder = defaultFolder,
                request = promotedReq,
                unsavedRequestIdToDelete = requestId
            )
        }
    }


    /**
     * Normalizes a raw URL input by prepending "http://" if protocol prefix is missing.
     */
    @Suppress("HttpUrlsUsage", "unused")
    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith(
                "https://",
                ignoreCase = true
            )
        ) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Executes the currently selected request live over the network via [ExecutionHandler].
     * Dynamically delegates pre-request scripting, authentication formatting, network dispatching,
     * test script evaluation, and minimum loading window enforcement.
     * Guaranteed to reset [ApiStudioUiState.isExecuting] via try-finally so requests can be re-run indefinitely.
     */
    fun sendCurrentRequest() {
        var request = _uiState.value.selectedRequest
        if (request == null) {
            request = createUnsavedRequest()
        }
        if (_uiState.value.isExecuting) return

        _uiState.update {
            it.copy(
                isExecuting = true,
                scriptErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val outcome = executionHandler.executeSingleRequest(request)
                val updatedRequest = request.copy(testResults = outcome.testResults)
                if (_uiState.value.selectedRequest?.id == request.id) {
                    syncSelectedRequestInList(updatedRequest)
                }
                _uiState.update {
                    it.copy(
                        latestResult = outcome.result,
                        testResults = outcome.testResults,
                        scriptErrorMessage = outcome.scriptError,
                        responsePresentation = outcome.presentation,
                        isExecutionStale = false
                    )
                }
            } finally {
                // GUARANTEED reset — isExecuting is ALWAYS set to false regardless of:
                // success, network failure, pre-request script error, or any uncaught exception.
                _uiState.update { it.copy(isExecuting = false) }
            }
        }
    }


    private inline fun updateActiveRequest(transform: (SavedApiRequest) -> SavedApiRequest) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = transform(target)
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
    }

    fun updateScriptLanguage(language: ScriptLanguage) = updateActiveRequest { req ->
        req.copy(scripts = req.scripts.copy(language = language))
    }

    fun updateApiKeyName(name: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.ApiKey -> a.copy(name = name)
                else -> ApiRequestAuth.ApiKey(name = name)
            }
        )
    }

    fun updateApiKeyValue(value: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.ApiKey -> a.copy(value = value)
                else -> ApiRequestAuth.ApiKey(value = value)
            }
        )
    }

    fun updateApiKeyLocation(location: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.ApiKey -> a.copy(location = location)
                else -> ApiRequestAuth.ApiKey(location = location)
            }
        )
    }

    fun updateOauthHeaderPrefix(prefix: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.OAuth2 -> a.copy(headerPrefix = prefix)
                else -> ApiRequestAuth.OAuth2(headerPrefix = prefix)
            }
        )
    }

    fun updateAwsAccessKey(key: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(accessKey = key)
                else -> ApiRequestAuth.AwsSignature(accessKey = key)
            }
        )
    }

    fun updateAwsSecretKey(secret: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(secretKey = secret)
                else -> ApiRequestAuth.AwsSignature(secretKey = secret)
            }
        )
    }

    fun updateAwsRegion(region: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(region = region)
                else -> ApiRequestAuth.AwsSignature(region = region)
            }
        )
    }

    fun updateAwsService(service: String) = updateActiveRequest { req ->
        req.copy(
            auth = when (val a = req.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(service = service)
                else -> ApiRequestAuth.AwsSignature(service = service)
            }
        )
    }


    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createNewCollection(name: String) {
        val newCollection = ApiCollection(
            id = "c-${kotlin.uuid.Uuid.random()}",
            name = name,
            folders = listOf(
                CollectionFolder(
                    id = "f-${kotlin.uuid.Uuid.random()}",
                    name = name,
                    isExpanded = true,
                    requests = emptyList()
                )
            )
        )
        _uiState.update {
            it.copy(collections = it.collections + newCollection)
        }
        viewModelScope.launch {
            repository?.saveCollection(newCollection)
        }
    }


    @Suppress("unused")
    fun createNewFolder(collectionId: String, folderName: String) {
        val newFolder = CollectionFolder(
            id = "f-${kotlin.uuid.Uuid.random()}",
            name = folderName,
            isExpanded = true,
            requests = emptyList()
        )
        _uiState.update { state ->
            val updatedCollections = state.collections.map { col ->
                if (col.id == collectionId) col.copy(folders = col.folders + newFolder) else col
            }
            state.copy(collections = updatedCollections)
        }
        viewModelScope.launch {
            repository?.saveFolder(collectionId, newFolder)
        }
    }

    @Suppress("unused")
    fun createNewRequest(
        collectionId: String,
        folderId: String,
        name: String,
        method: HttpMethod = HttpMethod.GET,
        url: String = "https://httpbin.org/get"
    ) {
        val newRequest = SavedApiRequest(
            id = "r-${kotlin.uuid.Uuid.random()}",
            name = name,
            method = method,
            url = url
        )
        _uiState.update { state ->
            val updatedCollections = state.collections.map { col ->
                if (col.id == collectionId) {
                    val updatedFolders = col.folders.map { folder ->
                        if (folder.id == folderId) folder.copy(requests = folder.requests + newRequest) else folder
                    }
                    col.copy(folders = updatedFolders)
                } else col
            }
            state.copy(
                collections = updatedCollections,
                selectedRequest = newRequest
            )
        }
        viewModelScope.launch {
            repository?.saveRequest(collectionId, folderId, newRequest)
        }
    }

    fun runSuite(config: com.devuloopers.knet.ui.apistudio.handler.SuiteExecutionConfig) {
        val collections = _uiState.value.collections
        if (collections.isEmpty() || _uiState.value.isSuiteRunning) return

        _uiState.update { it.copy(isSuiteRunning = true, suiteRunSummary = null) }

        viewModelScope.launch {
            val summary = executionHandler.executeSuiteScope(
                scope = config.scope,
                collections = collections,
                currentRequest = _uiState.value.selectedRequest
            )
            _uiState.update {
                it.copy(
                    isSuiteRunning = false,
                    suiteRunSummary = summary
                )
            }
        }
    }

    fun runCollectionSuite(targetCollectionId: String? = null) {
        val collections = _uiState.value.collections
        if (collections.isEmpty()) return

        val scope = if (targetCollectionId != null) {
            com.devuloopers.knet.ui.apistudio.handler.SuiteExecutionScope.Collection(targetCollectionId)
        } else {
            com.devuloopers.knet.ui.apistudio.handler.SuiteExecutionScope.Collections(collections.map { it.id })
        }

        runSuite(com.devuloopers.knet.ui.apistudio.handler.SuiteExecutionConfig(scope = scope))
    }

    fun dismissRunnerModal() {
        _uiState.update { it.copy(isSuiteRunning = false, suiteRunSummary = null) }
    }

    private val postmanImporter = PostmanCollectionImporter()
    private val postmanExporter = PostmanCollectionExporter()

    fun importPostmanJson(jsonContent: String) {
        try {
            val importedCollection = postmanImporter.parseJson(jsonContent)
            _uiState.update { state ->
                state.copy(
                    collections = state.collections + importedCollection,
                    selectedRequest = importedCollection.folders.firstOrNull()?.requests?.firstOrNull()
                        ?: state.selectedRequest
                )
            }
            viewModelScope.launch {
                repository?.saveCollection(importedCollection)
            }
        } catch (_: Exception) {
            // Invalid JSON
        }
    }

    @Suppress("unused")
    fun exportCurrentCollectionJson(): String {
        val currentCollection = _uiState.value.collections.firstOrNull() ?: return "{}"
        return postmanExporter.exportToJson(currentCollection)
    }

    override fun onCleared() {
        super.onCleared()
        apiClient.close()
    }
}
