package com.devuloopers.knet.ui.apistudio.model

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary

/**
 * UI State data model for the API Studio Screen.
 *
 * @property collections List of API collections loaded from repository.
 * @property selectedRequest Currently selected request in the tree view.
 * @property activeReqTab Active tab in Middle column ("Body (JSON)", "Params", "Authorization", etc.).
 * @property activeRespTab Active tab in Right column ("Body", "Headers", "Cookies", "Tests").
 * @property isExecuting True when an API request is currently executing over the network.
 * @property latestResult The live [ExecutionResult] returned from the latest "Send Request" call.
 */
data class ApiStudioUiState(
    val collections: List<ApiCollection> = emptyList(),
    val selectedRequest: SavedApiRequest? = null,
    val activeReqTab: String = "Body (JSON)",
    val activeRespTab: String = "Body",
    val isExecuting: Boolean = false,
    val latestResult: ExecutionResult? = null,
    val searchQuery: String = "",
    val isSuiteRunning: Boolean = false,
    val suiteRunSummary: SuiteRunSummary? = null
)
