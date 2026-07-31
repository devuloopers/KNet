package com.devuloopers.knet.ui.apistudio.model

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary

import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage

/**
 * UI State data model for the API Studio Screen.
 *
 * @property collections List of API collections loaded from repository.
 * @property selectedRequest Currently selected request in the tree view.
 * @property activeReqTab Active tab in Middle column ("Body", "Params", "Authorization", etc.).
 * @property activeRespTab Active tab in Right column ("Body", "Headers", "Cookies", "Tests").
 * @property isExecuting True when an API request is currently executing over the network.
 * @property latestResult The live [ExecutionResult] returned from the latest "Send Request" call.
 */
data class ApiStudioUiState(
    val collections: List<ApiCollection> = emptyList(),
    val selectedRequest: SavedApiRequest? = null,
    val activeReqTab: String = "Body",
    val activeRespTab: String = "Body",
    val isExecuting: Boolean = false,
    val latestResult: ExecutionResult? = null,
    val responsePresentation: ResponsePresentation? = null,
    val searchQuery: String = "",
    val isSuiteRunning: Boolean = false,
    val suiteRunSummary: SuiteRunSummary? = null,
    val detectedPathParams: Map<String, String> = emptyMap(),
    val testResults: List<TestAssertionResult> = emptyList(),
    val scriptErrorMessage: String? = null,
    val unsavedRequests: List<SavedApiRequest> = emptyList(),
    val unsavedCounter: Int = 1,
    val draftRequest: SavedApiRequest = SavedApiRequest(
        id = "draft",
        name = "New Request",
        method = com.devuloopers.knet.domain.apistudio.model.HttpMethod.GET,
        url = "http://127.0.0.1:9090/api/test/get",
        headers = com.devuloopers.knet.domain.apistudio.model.defaultHeaders()
    ),
    val analysisResult: com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptAnalysisResult? = null,
    val isExecutionStale: Boolean = false
)





