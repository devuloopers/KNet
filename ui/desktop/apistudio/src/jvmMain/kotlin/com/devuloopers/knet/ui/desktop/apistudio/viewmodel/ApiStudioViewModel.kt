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
import kotlinx.coroutines.delay
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

    public companion object {
        /**
         * Minimum visual loading duration in milliseconds to prevent single-frame
         * visual flickering on ultra-fast responses (< 200ms).
         */
        public const val MIN_LOADING_DURATION_MS: Long = 200L
    }

    private val _uiState = MutableStateFlow(ApiStudioState())
    val uiState: StateFlow<ApiStudioState> = _uiState.asStateFlow()

    /**
     * Updates the target request URL string in UDF state and automatically synchronizes
     * parsed query parameters into the Params table state.
     *
     * @param url The raw URL string input from the URL bar (e.g. "http://localhost:9090/api/get?foo=bar").
     */
    fun updateUrl(url: String) {
        val parsedParams = parseQueryParamsFromUrl(url)
        _uiState.update { state ->
            state.copy(
                editorState = state.editorState.copy(
                    url = url,
                    queryParams = parsedParams
                )
            )
        }
    }

    /**
     * Updates query parameter key-value pairs in UDF state and automatically reconstructs
     * the target URL string in the URL bar to reflect parameter changes in real time.
     *
     * @param queryParams Key-value pairs representing request query parameters.
     */
    fun updateQueryParams(queryParams: List<Pair<String, String>>) {
        _uiState.update { state ->
            val currentUrl = state.editorState.url
            val baseUrl = if (currentUrl.contains("?")) currentUrl.substringBefore("?") else currentUrl
            val activeParams = queryParams.filter { it.first.isNotBlank() }
            val newUrl = if (activeParams.isNotEmpty()) {
                val queryString = activeParams.joinToString("&") { "${it.first}=${it.second}" }
                "$baseUrl?$queryString"
            } else {
                baseUrl
            }
            state.copy(
                editorState = state.editorState.copy(
                    url = newUrl,
                    queryParams = queryParams
                )
            )
        }
    }

    /**
     * Helper function parsing a raw URL string into key-value query parameter pairs.
     *
     * @param url Target URL string containing optional `?key=value` query string.
     * @return List of key-value pairs extracted from query string.
     */
    private fun parseQueryParamsFromUrl(url: String): List<Pair<String, String>> {
        if (!url.contains("?")) return emptyList()
        val queryString = url.substringAfter("?").substringBefore("#")
        if (queryString.isBlank()) return emptyList()
        return queryString.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            val key = parts.getOrNull(0)?.trim() ?: ""
            if (key.isNotBlank()) {
                val value = parts.getOrNull(1)?.trim() ?: ""
                key to value
            } else null
        }
    }

    /**
     * Updates HTTP method in UDF state and synchronizes active tab header display.
     *
     * @param method Selected HTTP method string (GET, POST, PUT, DELETE, etc.).
     */
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

    /**
     * Updates request headers in UDF state.
     *
     * @param headers List of header key-value pairs.
     */
    fun updateHeaders(headers: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(headers = headers)) }
    }

    /**
     * Updates request body payload text content in UDF state.
     *
     * @param bodyPayload Raw request body string content.
     */
    fun updateBodyPayload(bodyPayload: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(bodyPayload = bodyPayload)) }
    }

    /**
     * Updates request body type (JSON, Form, Raw, None) in UDF state.
     *
     * @param bodyType Selected body mode representation string.
     */
    fun updateBodyType(bodyType: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(bodyType = bodyType)) }
    }

    /**
     * Updates authentication configuration type and credential token in UDF state.
     *
     * @param authType Selected authentication type (No Auth, Bearer Token, Basic Auth).
     * @param authToken Credential token string.
     */
    fun updateAuth(authType: String, authToken: String) {
        _uiState.update {
            it.copy(editorState = it.editorState.copy(authType = authType, authToken = authToken))
        }
    }

    /**
     * Updates request cookies in UDF state.
     *
     * @param cookies List of cookie key-value pairs.
     */
    fun updateCookies(cookies: List<Pair<String, String>>) {
        _uiState.update { it.copy(editorState = it.editorState.copy(cookies = cookies)) }
    }

    /**
     * Updates pre-request and test scripts in UDF state.
     *
     * @param preRequestScript Script code executed before request execution.
     * @param testScript Script code executed after response receipt.
     */
    fun updateScripts(preRequestScript: String, testScript: String) {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(
                    preRequestScript = preRequestScript,
                    testScript = testScript
                )
            )
        }
    }

    /**
     * Updates the active sub-tab selection (PARAMS, AUTH, HEADERS, BODY, COOKIES, SCRIPTS) in UDF state.
     *
     * @param subTabName Name of the active sub-tab.
     */
    fun updateActiveSubTab(subTabName: String) {
        _uiState.update { it.copy(editorState = it.editorState.copy(activeSubTab = subTabName)) }
    }

    /**
     * Clears current HTTP response presentation state and resets execution status to IDLE.
     */
    fun clearResponse() {
        _uiState.update { state ->
            state.copy(
                responsePresentation = null,
                executionState = ExecutionState.IDLE
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

        val executionStartTime = System.currentTimeMillis()

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

                val presentation = ResponsePresentation(
                    statusCode = result.statusCode,
                    statusText = result.statusText,
                    durationMs = result.latencyMs,
                    sizeBytes = result.responseSizeBytes,
                    mimeType = if (detectedMime != com.devuloopers.knet.domain.clientNetwork.model.MimeType.UNKNOWN) detectedMime.value else "text/plain",
                    headers = result.headers,
                    cookies = result.cookies,
                    body = formattedBody,
                    testResults = emptyList(),
                    consoleLogs = emptyList()
                )

                // Enforce minimum visual loading duration window to prevent UI flickering on ultra-fast responses (< 200ms)
                val elapsedMs = System.currentTimeMillis() - executionStartTime
                if (elapsedMs < MIN_LOADING_DURATION_MS) {
                    delay(MIN_LOADING_DURATION_MS - elapsedMs)
                }

                _uiState.update {
                    it.copy(
                        executionState = if (result.isSuccess) ExecutionState.SUCCESS else ExecutionState.ERROR,
                        responsePresentation = presentation,
                        errorMessage = result.errorMessage
                    )
                }
            } catch (e: Exception) {
                val elapsedMs = System.currentTimeMillis() - executionStartTime
                if (elapsedMs < MIN_LOADING_DURATION_MS) {
                    delay(MIN_LOADING_DURATION_MS - elapsedMs)
                }

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
