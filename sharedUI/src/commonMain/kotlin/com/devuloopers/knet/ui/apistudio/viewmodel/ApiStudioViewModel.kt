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

class ApiStudioViewModel(
    private val repository: CollectionsRepository? = null,
    private val proxyPort: Int? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiStudioUiState())
    val uiState: StateFlow<ApiStudioUiState> = _uiState.asStateFlow()

    private val apiClient = KNetApiClient(proxyPort)
    private val testRunner = CollectionTestRunner()

    // Seed default sample collection data if repository is empty
    init {
        val sampleFolders = listOf(
            CollectionFolder(
                id = "f-1",
                name = "Authentication API Suite",
                isExpanded = true,
                requests = listOf(
                    SavedApiRequest(
                        id = "r-1",
                        name = "/v1/auth/login",
                        method = HttpMethod.POST,
                        url = "https://httpbin.org/post",
                        body = "{\n  \"username\": \"developer@knet.dev\",\n  \"auth_type\": \"bearer\",\n  \"client_id\": \"knet_desktop_v2\"\n}",
                        testResults = listOf(
                            TestAssertionResult("t-1", "Status code is 200", true),
                            TestAssertionResult("t-2", "Response contains access_token", true)
                        )
                    ),
                    SavedApiRequest(
                        id = "r-2",
                        name = "/v1/auth/user-profile",
                        method = HttpMethod.GET,
                        url = "https://httpbin.org/get"
                    )
                )
            )
        )
        val defaultCollection = ApiCollection("c-1", "Default API Collection", sampleFolders)
        _uiState.update {
            it.copy(
                collections = listOf(defaultCollection),
                selectedRequest = sampleFolders[0].requests[0]
            )
        }
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
     */
    fun sendCurrentRequest() {
        val request = _uiState.value.selectedRequest ?: return
        if (_uiState.value.isExecuting) return

        _uiState.update { it.copy(isExecuting = true) }

        viewModelScope.launch {
            val result = apiClient.execute(
                url = request.url,
                method = request.methodString,
                headers = request.headers,
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

            _uiState.update {
                it.copy(
                    isExecuting = false,
                    latestResult = domainResult
                )
            }
        }
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
                    headers = req.headers,
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
