package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestTab
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponsePresentation
import com.devuloopers.knet.ui.desktop.apistudio.model.TestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing UDF state for HTTP API request authoring and execution.
 *
 * Dependencies are provided by Koin via [com.devuloopers.knet.ui.desktop.apistudio.di.apiStudioUiModule].
 *
 * @param executeUseCase Use case for executing client HTTP API requests.
 * @param formatResponseBodyUseCase Use case for formatting raw response bodies.
 */
class ApiStudioViewModel(
    private val executeUseCase: ExecuteClientApiRequestUseCase,
    private val formatResponseBodyUseCase: FormatResponseBodyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiStudioState())
    val uiState: StateFlow<ApiStudioState> = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(url = url)) }
    }

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

    fun selectEnvironment(envName: String) {
        _uiState.update { it.copy(selectedEnvironment = envName) }
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
        val newTab = RequestTab(newId, "Untitled")
        _uiState.update { state ->
            state.copy(tabs = state.tabs + newTab, activeTabId = newId)
        }
    }

    fun executeRequest() {
        val currentEditor = _uiState.value.editorState
        _uiState.update { it.copy(executionState = ExecutionState.EXECUTING, errorMessage = null) }

        viewModelScope.launch {
            try {
                val headerMap = currentEditor.headers.toMap()
                val queryParamMap = currentEditor.queryParams.toMap()
                val cookieMap = currentEditor.cookies.toMap()

                val authConfig = when (currentEditor.authType.lowercase()) {
                    "bearer token", "bearer" -> com.devuloopers.knet.domain.collection.model.ApiRequestAuth.Bearer(currentEditor.authToken)
                    "api key", "apikey" -> com.devuloopers.knet.domain.collection.model.ApiRequestAuth.ApiKey(value = currentEditor.authToken)
                    "basic auth", "basic" -> {
                        val parts = currentEditor.authToken.split(":", limit = 2)
                        com.devuloopers.knet.domain.collection.model.ApiRequestAuth.Basic(
                            username = parts.getOrNull(0) ?: "",
                            password = parts.getOrNull(1) ?: ""
                        )
                    }
                    else -> com.devuloopers.knet.domain.collection.model.ApiRequestAuth.None
                }

                val httpMethodEnum = try {
                    com.devuloopers.knet.domain.collection.model.HttpMethod.valueOf(currentEditor.method.uppercase())
                } catch (_: Exception) {
                    com.devuloopers.knet.domain.collection.model.HttpMethod.GET
                }

                val bodyTypeEnum = when (currentEditor.bodyType.uppercase()) {
                    "JSON" -> com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.JSON
                    "XML" -> com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.XML
                    "FORM", "FORM_DATA" -> com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.FORM_DATA
                    "GRAPHQL" -> com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.GRAPHQL
                    "RAW", "RAW_TEXT" -> com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.RAW_TEXT
                    else -> com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.NONE
                }

                val result = executeUseCase(
                    url = currentEditor.url,
                    method = httpMethodEnum,
                    headers = headerMap,
                    queryParams = queryParamMap,
                    cookies = cookieMap,
                    body = if (bodyTypeEnum != com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType.NONE) currentEditor.bodyPayload else "",
                    bodyType = bodyTypeEnum,
                    auth = authConfig
                )

                val detectedMime = com.devuloopers.knet.domain.util.MimeTypeUtils.extractFromHeaders(result.headers)
                val formattedBody = formatResponseBodyUseCase.execute(
                    rawBody = result.responseBody,
                    mimeType = detectedMime
                )

                val testResults = listOf(
                    TestResult("Status code is 200", result.statusCode == 200),
                    TestResult("Response time is less than 500ms", result.latencyMs < 500L, if (result.latencyMs >= 500L) "Latency exceeded limit: ${result.latencyMs}ms" else null),
                    TestResult("Content-Type header is present", result.headers.keys.any { it.equals("content-type", ignoreCase = true) })
                )
                val consoleLogs = listOf(
                    "[INFO] Preparing ${currentEditor.method} request to ${currentEditor.url}",
                    "[INFO] Pre-request script executed cleanly (0 ms)",
                    "[NET] Connection established",
                    "[NET] Received response: ${result.statusCode} ${result.statusText} (${result.responseSizeBytes} bytes)",
                    "[TEST] Executed 3 test assertions (Passed: ${testResults.count { it.passed }}/${testResults.size})"
                )

                val presentation = ResponsePresentation(
                    statusCode = result.statusCode,
                    statusText = result.statusText,
                    durationMs = result.latencyMs,
                    sizeBytes = result.responseSizeBytes,
                    mimeType = if (detectedMime != com.devuloopers.knet.domain.clientNetwork.model.MimeType.UNKNOWN) detectedMime.value else "text/plain",
                    headers = result.headers,
                    cookies = result.cookies,
                    body = formattedBody,
                    testResults = testResults,
                    consoleLogs = consoleLogs
                )

                _uiState.update {
                    it.copy(
                        executionState = if (result.isSuccess) ExecutionState.SUCCESS else ExecutionState.ERROR,
                        responsePresentation = presentation,
                        errorMessage = result.errorMessage
                    )
                }
            } catch (e: Exception) {
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
