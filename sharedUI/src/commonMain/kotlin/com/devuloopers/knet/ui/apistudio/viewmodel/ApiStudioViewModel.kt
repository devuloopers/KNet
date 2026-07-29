package com.devuloopers.knet.ui.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.domain.apistudio.model.ApiRequestBody
import com.devuloopers.knet.domain.apistudio.model.ApiRequestScripts
import com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth
import com.devuloopers.knet.domain.apistudio.repository.CollectionsRepository
import com.devuloopers.knet.domain.apistudio.usecase.ExecuteApiRequestUseCase
import com.devuloopers.knet.engine.client.KNetApiClient
import com.devuloopers.knet.engine.client.model.AuthType
import com.devuloopers.knet.engine.client.model.RequestBodyType
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
 */
import com.devuloopers.knet.domain.apistudio.runner.CollectionTestRunner
import com.devuloopers.knet.domain.apistudio.runner.SuiteRequestResult
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary
import com.devuloopers.knet.domain.apistudio.importer.PostmanCollectionImporter
import com.devuloopers.knet.domain.apistudio.exporter.PostmanCollectionExporter

import com.devuloopers.knet.domain.apistudio.detector.UrlParameterExtractor
import com.devuloopers.knet.domain.apistudio.model.RequestHeader
import com.devuloopers.knet.domain.apistudio.model.defaultHeaders

class ApiStudioViewModel(
    private val repository: CollectionsRepository? = null,
    private val proxyPort: Int? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiStudioUiState())
    val uiState: StateFlow<ApiStudioUiState> = _uiState.asStateFlow()

    private val apiClient = KNetApiClient(proxyPort)
    private val testRunner = CollectionTestRunner()
    private val urlParameterExtractor = UrlParameterExtractor()

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


    private fun clearResponseState() {
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
        clearResponseState()
        val parseResult = urlParameterExtractor.extract(newUrl)
        var currentReq = _uiState.value.selectedRequest

        if (currentReq == null && newUrl.isNotBlank()) {
            currentReq = createUnsavedRequest(
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
    fun updateRequestBody(newBody: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(body = target.body.copy(content = newBody))
        if (_uiState.value.selectedRequest != null) {
            syncSelectedRequestInList(updated)
        } else {
            _uiState.update { it.copy(draftRequest = updated) }
        }
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
                currentHeaders[existingIndex] = currentHeaders[existingIndex].copy(value = contentTypeHeader, isEnabled = true)
            } else {
                currentHeaders.add(com.devuloopers.knet.domain.apistudio.model.RequestHeader(key = "Content-Type", value = contentTypeHeader, isEnabled = true, isAuto = false))
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


    fun updateAuthType(type: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val currentAuth = target.auth
        val updated = target.copy(
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
                    service = if (currentAuth is ApiRequestAuth.AwsSignature) currentAuth.service else "s3"
                )
                "Inherit Auth" -> ApiRequestAuth.Inherit
                else -> ApiRequestAuth.None
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
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
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateAuthUsername(username: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.Basic -> a.copy(username = username)
                else -> ApiRequestAuth.Basic(username, "")
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateAuthPassword(password: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.Basic -> a.copy(password = password)
                else -> ApiRequestAuth.Basic("", password)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updatePreRequestScript(script: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(scripts = target.scripts.copy(preRequest = script))
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateTestScript(script: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(scripts = target.scripts.copy(test = script))
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
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
            if (isDeleted) updatedCollections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull() else _uiState.value.selectedRequest
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
            headers = if (source.headers.isNotEmpty()) source.headers else defaultHeaders(),
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
                updatedUnsaved.lastOrNull() ?: state.collections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull()
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
        val unsavedReq = _uiState.value.unsavedRequests.find { it.id == requestId } ?: _uiState.value.selectedRequest ?: return
        val promotedReq = unsavedReq.copy(
            id = "req-${kotlin.uuid.Uuid.random()}",
            name = customName?.takeIf { it.isNotBlank() } ?: unsavedReq.name
        )

        val updatedCollections = _uiState.value.collections.map { col ->
            if (col.id == targetCollectionId) {
                val effectiveFolders = if (col.folders.isEmpty()) {
                    listOf(
                        com.devuloopers.knet.domain.apistudio.model.CollectionFolder(
                            id = "folder-${kotlin.uuid.Uuid.random()}",
                            name = "General",
                            requests = emptyList()
                        )
                    )
                } else col.folders
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
            val folderId = targetFolderId ?: _uiState.value.collections.find { it.id == targetCollectionId }?.folders?.firstOrNull()?.id ?: ""
            repository?.saveRequest(targetCollectionId, folderId, promotedReq)
        }
    }


    /**
     * Normalizes a raw URL input by prepending "http://" if protocol prefix is missing.
     */
    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Executes the currently selected request live over the network via [KNetApiClient].
     * Dynamically formats and injects active [ApiStudioUiState.authType] credentials into headers/params.
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
                val state = _uiState.value
                val finalHeaders = request.headers
                    .filter { it.isEnabled && !it.value.startsWith("<") }
                    .associate { it.key to it.value }
                    .toMutableMap()

                var finalUrl = normalizeUrl(request.url)

                when (val auth = request.auth) {
                    is ApiRequestAuth.Bearer -> {
                        if (auth.token.isNotBlank()) {
                            finalHeaders["Authorization"] = "Bearer ${auth.token}"
                        }
                    }
                    is ApiRequestAuth.ApiKey -> {
                        val keyName = auth.name.ifBlank { "X-API-Key" }
                        if (auth.value.isNotBlank()) {
                            if (auth.location.equals("Header", ignoreCase = true)) {
                                finalHeaders[keyName] = auth.value
                            } else {
                                val separator = if (finalUrl.contains("?")) "&" else "?"
                                finalUrl += "$separator$keyName=${auth.value}"
                            }
                        }
                    }
                    is ApiRequestAuth.Basic -> {
                        if (auth.username.isNotBlank() || auth.password.isNotBlank()) {
                            val raw = "${auth.username}:${auth.password}"
                            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                            val encoded = kotlin.io.encoding.Base64.Default.encode(raw.encodeToByteArray())
                            finalHeaders["Authorization"] = "Basic $encoded"
                        }
                    }
                    is ApiRequestAuth.OAuth2 -> {
                        val prefix = auth.headerPrefix.ifBlank { "Bearer" }
                        if (auth.token.isNotBlank()) {
                            finalHeaders["Authorization"] = "$prefix ${auth.token}"
                        }
                    }
                    is ApiRequestAuth.AwsSignature -> {
                        if (auth.accessKey.isNotBlank()) {
                            finalHeaders["Authorization"] = "AWS4-HMAC-SHA256 Credential=${auth.accessKey}/${auth.region}/${auth.service}/aws4_request"
                        }
                    }
                    else -> {}
                }

                // Execute Pre-request Script before sending network request
                if (request.scripts.preRequest.isNotBlank()) {
                    val scriptReq = com.devuloopers.knet.scriptengine.api.ScriptRequestModel(
                        url = finalUrl,
                        method = request.methodString,
                        headers = finalHeaders,
                        queryParams = mutableMapOf(),
                        body = request.body.content
                    )
                    val scriptRuntime = com.devuloopers.knet.scriptengine.runtime.ScriptRuntime()
                    when (val preRes = scriptRuntime.executeScript(request.scripts.preRequest, request.scripts.language, scriptReq)) {
                        is com.devuloopers.knet.scriptengine.api.ScriptExecutionResult.Success -> {
                            finalUrl = preRes.request.url
                            finalHeaders.putAll(preRes.request.headers)
                        }
                        is com.devuloopers.knet.scriptengine.api.ScriptExecutionResult.Error -> {
                            // Use state update + throw so the finally block ALWAYS resets isExecuting.
                            // return@launch would bypass finally and permanently block SEND.
                            _uiState.update {
                                it.copy(scriptErrorMessage = "Pre-request Error: ${preRes.message}")
                            }
                            throw IllegalStateException("Pre-request script failed: ${preRes.message}")
                        }
                    }
                }


                val result = apiClient.execute(
                    url = finalUrl,
                    method = request.methodString,
                    headers = finalHeaders,
                    body = request.body.content,
                    bodyType = RequestBodyType.JSON
                )

                val domainResult = com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult(
                    statusCode = result.statusCode,
                    statusText = result.statusText,
                    headers = result.headers,
                    responseBody = result.responseBody,
                    latencyMs = result.latencyMs,
                    responseSizeBytes = result.responseSizeBytes,
                    isSuccess = result.isSuccess,
                    errorMessage = result.errorMessage
                )

                val testResults = testRunner.evaluateAssertions(
                    request = request,
                    result = domainResult,
                    testScript = request.scripts.test,
                    scriptLanguage = request.scripts.language
                )

                _uiState.update {
                    it.copy(
                        latestResult = domainResult,
                        testResults = testResults
                    )
                }
            } catch (e: Exception) {
                // Only overwrite latestResult with a network error if this is NOT a pre-request script
                // failure (which already set scriptErrorMessage). Avoids masking the script error UI.
                if (_uiState.value.scriptErrorMessage == null) {
                    val errorResult = com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult(
                        statusCode = 0,
                        statusText = "Network Error",
                        headers = emptyMap(),
                        responseBody = "",
                        latencyMs = 0L,
                        responseSizeBytes = 0L,
                        isSuccess = false,
                        errorMessage = e.message ?: e.toString()
                    )
                    _uiState.update {
                        it.copy(
                            latestResult = errorResult,
                            testResults = emptyList()
                        )
                    }
                }
            } finally {
                // GUARANTEED reset — isExecuting is ALWAYS set to false regardless of:
                // success, network failure, pre-request script error, or any uncaught exception.
                _uiState.update { it.copy(isExecuting = false) }
            }
        }
    }



    fun updateScriptLanguage(language: com.devuloopers.knet.scriptengine.api.ScriptLanguage) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(scripts = target.scripts.copy(language = language))
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateApiKeyName(name: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.ApiKey -> a.copy(name = name)
                else -> ApiRequestAuth.ApiKey(name = name)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateApiKeyValue(value: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.ApiKey -> a.copy(value = value)
                else -> ApiRequestAuth.ApiKey(value = value)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateApiKeyLocation(location: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.ApiKey -> a.copy(location = location)
                else -> ApiRequestAuth.ApiKey(location = location)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateOauthHeaderPrefix(prefix: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.OAuth2 -> a.copy(headerPrefix = prefix)
                else -> ApiRequestAuth.OAuth2(headerPrefix = prefix)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateAwsAccessKey(key: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(accessKey = key)
                else -> ApiRequestAuth.AwsSignature(accessKey = key)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateAwsSecretKey(secret: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(secretKey = secret)
                else -> ApiRequestAuth.AwsSignature(secretKey = secret)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateAwsRegion(region: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(region = region)
                else -> ApiRequestAuth.AwsSignature(region = region)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
    }

    fun updateAwsService(service: String) {
        val target = _uiState.value.selectedRequest ?: _uiState.value.draftRequest
        val updated = target.copy(
            auth = when (val a = target.auth) {
                is ApiRequestAuth.AwsSignature -> a.copy(service = service)
                else -> ApiRequestAuth.AwsSignature(service = service)
            }
        )
        if (_uiState.value.selectedRequest != null) syncSelectedRequestInList(updated) else _uiState.update { it.copy(draftRequest = updated) }
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

    fun runCollectionSuite() {
        val allRequests = _uiState.value.collections.flatMap { col ->
            col.folders.flatMap { f -> f.requests }
        }
        if (allRequests.isEmpty() || _uiState.value.isSuiteRunning) return

        _uiState.update { it.copy(isSuiteRunning = true, suiteRunSummary = null) }

        viewModelScope.launch {
            val resultsList = mutableListOf<SuiteRequestResult>()

            allRequests.forEach { req ->
                val res = apiClient.execute(
                    url = req.url,
                    method = req.methodString,
                    headers = req.headers
                        .filter { it.isEnabled && !it.value.startsWith("<") }
                        .associate { it.key to it.value },
                    body = req.body.content
                )
                val domainRes = com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult(
                    statusCode = res.statusCode,
                    statusText = res.statusText,
                    headers = res.headers,
                    responseBody = res.responseBody,
                    latencyMs = res.latencyMs,
                    responseSizeBytes = res.responseSizeBytes,
                    isSuccess = res.isSuccess
                )
                val assertions = testRunner.evaluateAssertions(req, domainRes)
                resultsList.add(SuiteRequestResult(req, domainRes, assertions))
            }

            val passed = resultsList.count { it.executionResult.isSuccess }
            val failed = resultsList.size - passed
            val avgLatency = if (resultsList.isNotEmpty()) resultsList.map { it.executionResult.latencyMs }.average().toLong() else 0L

            val summary = SuiteRunSummary(
                totalRequests = resultsList.size,
                passedCount = passed,
                failedCount = failed,
                averageLatencyMs = avgLatency,
                results = resultsList
            )

            _uiState.update {
                it.copy(
                    isSuiteRunning = false,
                    suiteRunSummary = summary
                )
            }
        }
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
                    selectedRequest = importedCollection.folders.firstOrNull()?.requests?.firstOrNull() ?: state.selectedRequest
                )
            }
            viewModelScope.launch {
                repository?.saveCollection(importedCollection)
            }
        } catch (_: Exception) {
            // Invalid JSON
        }
    }

    fun exportCurrentCollectionJson(): String {
        val currentCollection = _uiState.value.collections.firstOrNull() ?: return "{}"
        return postmanExporter.exportToJson(currentCollection)
    }

    override fun onCleared() {
        super.onCleared()
        apiClient.close()
    }
}
