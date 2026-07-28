package com.devuloopers.knet.ui.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
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
        loadDefaultSampleCollection()
    }

    private fun loadDefaultSampleCollection() {
        val defaultReq = SavedApiRequest(
            id = "req-1",
            name = "GET Live Echo",
            method = HttpMethod.GET,
            url = "http://127.0.0.1:9090/api/test/get"
        )
        val defaultFolder = CollectionFolder(
            id = "folder-1",
            name = "WebFlux Test Suite",
            requests = listOf(
                defaultReq,
                SavedApiRequest(
                    id = "req-2",
                    name = "POST JSON Echo",
                    method = HttpMethod.POST,
                    url = "http://127.0.0.1:9090/api/test/post/json",
                    body = "{\n  \"name\": \"KNet User\",\n  \"role\": \"Developer\"\n}"
                ),
                SavedApiRequest(
                    id = "req-3",
                    name = "GET Bearer Auth",
                    method = HttpMethod.GET,
                    url = "http://127.0.0.1:9090/api/test/auth/bearer"
                ),
                SavedApiRequest(
                    id = "req-4",
                    name = "GET Status 200",
                    method = HttpMethod.GET,
                    url = "http://127.0.0.1:9090/api/test/status/200"
                )
            )
        )
        val defaultCollection = ApiCollection(
            id = "col-1",
            name = "KNet Local Test Server (WebFlux)",
            folders = listOf(defaultFolder)
        )

        _uiState.update { state ->
            if (state.collections.isEmpty()) {
                state.copy(
                    collections = listOf(defaultCollection),
                    selectedRequest = defaultReq
                )
            } else state
        }
    }


    /**
     * Called on every URL field keypress. Extracts path variables and query params
     * and updates the selected request URL without touching the headers.
     */
    fun onUrlInputChanged(newUrl: String) {
        val selectedReq = _uiState.value.selectedRequest ?: return
        val parseResult = urlParameterExtractor.extract(newUrl)
        _uiState.update { state ->
            state.copy(
                selectedRequest = selectedReq.copy(url = newUrl),
                detectedPathParams = parseResult.pathVariables
            )
        }
    }

    /**
     * Toggles the enabled/disabled state of a header row by key.
     * Disabled headers are not sent with the request.
     */
    fun toggleHeader(key: String) {
        val selectedReq = _uiState.value.selectedRequest ?: return
        val updatedHeaders = selectedReq.headers.map { header ->
            if (header.key == key) header.copy(isEnabled = !header.isEnabled) else header
        }
        _uiState.update { it.copy(selectedRequest = selectedReq.copy(headers = updatedHeaders)) }
    }

    /**
     * Updates the value of a header row identified by key.
     */
    fun updateHeaderValue(key: String, newValue: String) {
        val selectedReq = _uiState.value.selectedRequest ?: return
        val updatedHeaders = selectedReq.headers.map { header ->
            if (header.key == key) header.copy(value = newValue) else header
        }
        _uiState.update { it.copy(selectedRequest = selectedReq.copy(headers = updatedHeaders)) }
    }

    /**
     * Adds a new user-defined header row to the selected request.
     */
    fun addHeader(key: String = "", value: String = "") {
        val selectedReq = _uiState.value.selectedRequest ?: return
        val newHeader = RequestHeader(key = key, value = value, isEnabled = true, isAuto = false)
        _uiState.update {
            it.copy(selectedRequest = selectedReq.copy(headers = selectedReq.headers + newHeader))
        }
    }

    /**
     * Removes a header row by key from the selected request.
     * Auto headers can be restored later via [restoreDefaultHeaders].
     */
    fun removeHeader(key: String) {
        val selectedReq = _uiState.value.selectedRequest ?: return
        val updatedHeaders = selectedReq.headers.filter { it.key != key }
        _uiState.update { it.copy(selectedRequest = selectedReq.copy(headers = updatedHeaders)) }
    }

    /**
     * Updates the key of a header row.
     */
    fun updateHeaderKey(oldKey: String, newKey: String) {
        val selectedReq = _uiState.value.selectedRequest ?: return
        val updatedHeaders = selectedReq.headers.map { header ->
            if (header.key == oldKey) header.copy(key = newKey, isAuto = false) else header
        }
        _uiState.update { it.copy(selectedRequest = selectedReq.copy(headers = updatedHeaders)) }
    }

    /**
     * Updates the request payload body for the selected request.
     */
    fun updateRequestBody(newBody: String) {
        val selectedReq = _uiState.value.selectedRequest ?: return
        _uiState.update { it.copy(selectedRequest = selectedReq.copy(body = newBody)) }
    }

    /**
     * Updates the request body mode (none, json, form-data, x-www-form-urlencoded, raw, graphql)
     * and auto-syncs the Content-Type header accordingly.
     */
    fun updateRequestBodyType(newType: String) {
        val selectedReq = _uiState.value.selectedRequest ?: return
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

        _uiState.update {
            it.copy(selectedRequest = selectedReq.copy(bodyType = newType, headers = currentHeaders))
        }
    }

    /**
     * Restores any deleted auto-generated default headers back to the selected request.
     */
    fun restoreDefaultHeaders() {
        val selectedReq = _uiState.value.selectedRequest ?: return
        val existingKeys = selectedReq.headers.map { it.key }.toSet()
        val missingDefaults = defaultHeaders().filter { it.key !in existingKeys }
        _uiState.update {
            it.copy(selectedRequest = selectedReq.copy(headers = missingDefaults + selectedReq.headers))
        }
    }

    fun updateAuthType(type: String) {
        _uiState.update { it.copy(authType = type) }
    }

    fun updateAuthToken(token: String) {
        _uiState.update { it.copy(authToken = token) }
    }

    fun updateAuthUsername(username: String) {
        _uiState.update { it.copy(authUsername = username) }
    }

    fun updateAuthPassword(password: String) {
        _uiState.update { it.copy(authPassword = password) }
    }

    fun updatePreRequestScript(script: String) {
        _uiState.update { it.copy(preRequestScript = script) }
    }

    fun updateTestScript(script: String) {
        _uiState.update { it.copy(testScript = script) }
    }

    init {
        val initialReq = SavedApiRequest(id = "r-default", name = "New Request", method = HttpMethod.GET, url = "")
        _uiState.update { it.copy(selectedRequest = initialReq) }

        // Observe collections from repository if provided
        if (repository != null) {
            viewModelScope.launch {
                repository.observeCollections().collect { loadedCollections ->
                    _uiState.update { state ->
                        val firstReq = loadedCollections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull()
                        state.copy(
                            collections = loadedCollections,
                            selectedRequest = firstReq ?: state.selectedRequest ?: initialReq
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
        _uiState.update { it.copy(selectedRequest = request) }
    }

    fun selectReqTab(tab: String) {
        _uiState.update { it.copy(activeReqTab = tab) }
    }

    fun selectRespTab(tab: String) {
        _uiState.update { it.copy(activeRespTab = tab) }
    }

    /**
     * Executes the currently selected request live over the network via [KNetApiClient].
     * Dynamically formats and injects active [ApiStudioUiState.authType] credentials into headers/params.
     */
    fun sendCurrentRequest() {
        val request = _uiState.value.selectedRequest ?: return
        if (_uiState.value.isExecuting) return

        _uiState.update { it.copy(isExecuting = true) }

        viewModelScope.launch {
            val state = _uiState.value
            val finalHeaders = request.headers
                .filter { it.isEnabled && !it.value.startsWith("<") }
                .associate { it.key to it.value }
                .toMutableMap()

            var finalUrl = request.url

            when (state.authType) {
                "Bearer Token" -> {
                    if (state.authToken.isNotBlank()) {
                        finalHeaders["Authorization"] = "Bearer ${state.authToken}"
                    }
                }
                "API Key" -> {
                    val keyName = state.apiKeyName.ifBlank { "X-API-Key" }
                    if (state.apiKeyValue.isNotBlank()) {
                        if (state.apiKeyLocation.equals("Header", ignoreCase = true)) {
                            finalHeaders[keyName] = state.apiKeyValue
                        } else {
                            val separator = if (finalUrl.contains("?")) "&" else "?"
                            finalUrl += "$separator$keyName=${state.apiKeyValue}"
                        }
                    }
                }
                "Basic Auth" -> {
                    if (state.authUsername.isNotBlank() || state.authPassword.isNotBlank()) {
                        val raw = "${state.authUsername}:${state.authPassword}"
                        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                        val encoded = kotlin.io.encoding.Base64.Default.encode(raw.encodeToByteArray())
                        finalHeaders["Authorization"] = "Basic $encoded"
                    }
                }
                "OAuth 2.0" -> {
                    val prefix = state.oauthHeaderPrefix.ifBlank { "Bearer" }
                    if (state.authToken.isNotBlank()) {
                        finalHeaders["Authorization"] = "$prefix ${state.authToken}"
                    }
                }
                "AWS Signature" -> {
                    if (state.awsAccessKey.isNotBlank()) {
                        finalHeaders["Authorization"] = "AWS4-HMAC-SHA256 Credential=${state.awsAccessKey}/${state.awsRegion}/${state.awsService}/aws4_request"
                    }
                }
            }

            // Execute Pre-request Script before sending network request
            if (state.preRequestScript.isNotBlank()) {
                val scriptReq = com.devuloopers.knet.scriptengine.api.ScriptRequestModel(
                    url = finalUrl,
                    method = request.methodString,
                    headers = finalHeaders,
                    queryParams = mutableMapOf(),
                    body = request.body
                )
                val scriptRuntime = com.devuloopers.knet.scriptengine.runtime.ScriptRuntime()
                when (val preRes = scriptRuntime.executeScript(state.preRequestScript, state.scriptLanguage, scriptReq)) {
                    is com.devuloopers.knet.scriptengine.api.ScriptExecutionResult.Success -> {
                        finalUrl = preRes.request.url
                        finalHeaders.putAll(preRes.request.headers)
                    }
                    is com.devuloopers.knet.scriptengine.api.ScriptExecutionResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isExecuting = false,
                                scriptErrorMessage = "Pre-request Error: ${preRes.message}"
                            )
                        }
                        return@launch
                    }
                }
            }

            val result = apiClient.execute(
                url = finalUrl,
                method = request.methodString,
                headers = finalHeaders,
                body = request.body,
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
                testScript = state.testScript,
                scriptLanguage = state.scriptLanguage
            )

            _uiState.update {
                it.copy(
                    isExecuting = false,
                    latestResult = domainResult,
                    testResults = testResults
                )
            }
        }
    }

    fun updateScriptLanguage(language: com.devuloopers.knet.scriptengine.api.ScriptLanguage) {
        _uiState.update { it.copy(scriptLanguage = language) }
    }

    fun updateApiKeyName(name: String) {
        _uiState.update { it.copy(apiKeyName = name) }
    }


    fun updateApiKeyValue(value: String) {
        _uiState.update { it.copy(apiKeyValue = value) }
    }

    fun updateApiKeyLocation(location: String) {
        _uiState.update { it.copy(apiKeyLocation = location) }
    }

    fun updateOauthHeaderPrefix(prefix: String) {
        _uiState.update { it.copy(oauthHeaderPrefix = prefix) }
    }

    fun updateAwsAccessKey(key: String) {
        _uiState.update { it.copy(awsAccessKey = key) }
    }

    fun updateAwsSecretKey(secret: String) {
        _uiState.update { it.copy(awsSecretKey = secret) }
    }

    fun updateAwsRegion(region: String) {
        _uiState.update { it.copy(awsRegion = region) }
    }

    fun updateAwsService(service: String) {
        _uiState.update { it.copy(awsService = service) }
    }



    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createNewCollection(name: String) {
        val newCollection = ApiCollection(
            id = "c-${System.currentTimeMillis()}",
            name = name,
            folders = listOf(
                CollectionFolder(
                    id = "f-${System.currentTimeMillis()}",
                    name = "General",
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
            id = "f-${System.currentTimeMillis()}",
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
            id = "r-${System.currentTimeMillis()}",
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
                    body = req.body
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
