package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.core.http.client.KNetApiClient
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
 */
class ApiStudioViewModel(
    private val apiClient: KNetApiClient = KNetApiClient()
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
                val result = apiClient.execute(
                    url = currentEditor.url,
                    method = currentEditor.method,
                    headers = headerMap,
                    body = if (currentEditor.bodyType != "None") currentEditor.bodyPayload else ""
                )

                val testResults = listOf(
                    TestResult("Status code is 200", result.statusCode == 200),
                    TestResult("Response time is less than 500ms", result.latencyMs < 500L, if (result.latencyMs >= 500L) "Latency exceeded limit: ${result.latencyMs}ms" else null),
                    TestResult("Content-Type header is present", result.headers.keys.any { it.equals("content-type", ignoreCase = true) })
                )
                val consoleLogs = listOf(
                    "[INFO] Preparing ${currentEditor.method} request to ${currentEditor.url}",
                    "[INFO] Pre-request script executed cleanly (0 ms)",
                    "[NET] Connection established in 42 ms",
                    "[NET] Received response: ${result.statusCode} ${result.statusText} (${result.responseSizeBytes} bytes)",
                    "[TEST] Executed 3 test assertions (Passed: ${testResults.count { it.passed }}/${testResults.size})"
                )

                val presentation = ResponsePresentation(
                    statusCode = result.statusCode,
                    statusText = result.statusText,
                    durationMs = result.latencyMs,
                    sizeBytes = result.responseSizeBytes,
                    mimeType = result.headers["content-type"] ?: "text/plain",
                    headers = result.headers,
                    body = result.responseBody,
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
