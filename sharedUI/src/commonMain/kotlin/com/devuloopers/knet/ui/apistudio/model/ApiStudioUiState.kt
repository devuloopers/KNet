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
    val searchQuery: String = "",
    val isSuiteRunning: Boolean = false,
    val suiteRunSummary: SuiteRunSummary? = null,
    val detectedPathParams: Map<String, String> = emptyMap(),
    val authType: String = "Bearer Token",
    val authToken: String = "",
    val authUsername: String = "",
    val authPassword: String = "",
    val apiKeyName: String = "X-API-Key",
    val apiKeyValue: String = "",
    val apiKeyLocation: String = "Header",
    val oauthHeaderPrefix: String = "Bearer",
    val awsAccessKey: String = "",
    val awsSecretKey: String = "",
    val awsRegion: String = "us-east-1",
    val awsService: String = "s3",
    val preRequestScript: String = "",
    val testScript: String = "",
    val scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val testResults: List<TestAssertionResult> = emptyList(),
    val scriptErrorMessage: String? = null
)



