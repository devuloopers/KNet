package com.devuloopers.knet.ui.desktop.apistudio.usecase

import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.MimeType
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toRequestBodyType
import com.devuloopers.knet.engine.script.api.EnvironmentStore
import com.devuloopers.knet.engine.script.api.ScriptExecutionResult
import com.devuloopers.knet.engine.script.api.ScriptLanguage
import com.devuloopers.knet.engine.script.api.ScriptRequestModel
import com.devuloopers.knet.engine.script.api.ScriptResponseModel
import com.devuloopers.knet.engine.script.runtime.ScriptRuntime
import com.devuloopers.knet.ui.desktop.apistudio.model.AuthType
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.TestResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result data DTO returned after scripted API execution.
 *
 * @property result Raw low-level execution result.
 * @property formattedBody Formatted response payload string.
 * @property mimeType Detected MIME type.
 * @property testResults List of post-response script assertion test results.
 * @property consoleLogs List of console logs captured during pre and post script execution.
 */
public data class ScriptedExecutionResult(
    val result: ExecutionResult,
    val formattedBody: String,
    val mimeType: MimeType,
    val testResults: List<TestResult>,
    val consoleLogs: List<String>
)

/**
 * Presentation-layer UseCase orchestrating pre-request script execution, network call dispatching,
 * response formatting, and post-response test script assertion evaluation.
 *
 * @property executeUseCase Low-level HTTP client request execution UseCase.
 * @property formatResponseBodyUseCase Response body formatting UseCase.
 * @property ioDispatcher Coroutine dispatcher for background network/script I/O.
 */
public class ExecuteScriptedApiRequestUseCase(
    private val executeUseCase: ExecuteClientApiRequestUseCase,
    private val formatResponseBodyUseCase: FormatResponseBodyUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Executes an API request with optional pre-request scripts, network call execution, and post-response test assertions.
     *
     * @param editorState Current active request editor configuration state.
     * @param proxyPort Active proxy port, or null if direct connection.
     * @return Formatted [ScriptedExecutionResult].
     */
    public suspend fun execute(
        editorState: RequestEditorState,
        proxyPort: Int?
    ): ScriptedExecutionResult = withContext(ioDispatcher) {
        var effectiveUrl = editorState.url
        var headerMap = editorState.headers.sanitizeTransportHeaders().toMap()
        var queryParamMap = editorState.queryParams.toMap()
        var effectiveBody = editorState.bodyPayload

        val environmentStore = EnvironmentStore()
        val uiConsoleLogs = mutableListOf<String>()

        val targetLanguage = editorState.scriptLanguage

        // 1. Pre-request Script Execution
        if (editorState.preRequestScript.isNotBlank()) {
            val scriptReq = ScriptRequestModel(
                url = effectiveUrl,
                method = editorState.method,
                headers = headerMap.toMutableMap(),
                queryParams = queryParamMap.toMutableMap(),
                body = effectiveBody
            )
            val preScriptResult = ScriptRuntime.execute(
                language = targetLanguage,
                code = editorState.preRequestScript,
                request = scriptReq,
                response = null,
                environment = environmentStore
            )
            when (preScriptResult) {
                is ScriptExecutionResult.Success -> {
                    uiConsoleLogs.addAll(preScriptResult.logs)
                    effectiveUrl = preScriptResult.request.url
                    headerMap = preScriptResult.request.headers.toMap()
                    queryParamMap = preScriptResult.request.queryParams.toMap()
                    effectiveBody = preScriptResult.request.body
                }

                is ScriptExecutionResult.Error -> {
                    uiConsoleLogs.add("[Pre-request Error] ${preScriptResult.message}")
                }
            }
        }

        // 2. Auth Configuration Mapping
        val authConfig = when (editorState.authState.authType) {
            AuthType.BEARER_TOKEN -> ApiRequestAuth.Bearer(editorState.authState.bearerToken)
            AuthType.API_KEY -> ApiRequestAuth.ApiKey(
                name = editorState.authState.apiKeyName.ifBlank { "X-API-Key" },
                value = editorState.authState.apiKeyValue,
                location = editorState.authState.apiKeyLocation.label
            )
            AuthType.BASIC_AUTH -> ApiRequestAuth.Basic(
                username = editorState.authState.basicUsername,
                password = editorState.authState.basicPassword
            )
            else -> ApiRequestAuth.None
        }

        val httpMethodEnum = try {
            HttpMethod.valueOf(editorState.method.uppercase())
        } catch (_: Exception) {
            HttpMethod.GET
        }

        val bodyTypeEnum = editorState.bodyType.toRequestBodyType()

        val cookieMap = editorState.cookies.toMap()

        // 3. Network Execution
        val res = executeUseCase(
            url = effectiveUrl,
            method = httpMethodEnum,
            headers = headerMap,
            queryParams = queryParamMap,
            cookies = cookieMap,
            body = if (bodyTypeEnum != RequestBodyType.NONE) effectiveBody else "",
            bodyType = bodyTypeEnum,
            auth = authConfig,
            proxyPort = proxyPort
        )

        val mime = com.devuloopers.knet.domain.util.MimeTypeUtils.extractFromHeaders(res.headers)
        val bodyText = formatResponseBodyUseCase.execute(
            rawBody = res.responseBody,
            mimeType = mime
        )

        // 4. Post-response Test Script Execution
        var uiTestResults = emptyList<TestResult>()

        if (editorState.testScript.isNotBlank() && res.failureReason == null) {
            val scriptReq = ScriptRequestModel(
                url = editorState.url,
                method = editorState.method,
                headers = headerMap.toMutableMap(),
                queryParams = queryParamMap.toMutableMap(),
                body = editorState.bodyPayload
            )
            val scriptResp = ScriptResponseModel(
                statusCode = res.statusCode,
                statusText = res.statusText,
                latencyMs = res.latencyMs,
                responseSizeBytes = res.responseSizeBytes,
                headers = res.headers,
                body = res.responseBody
            )
            val scriptResult = ScriptRuntime.execute(
                language = targetLanguage,
                code = editorState.testScript,
                request = scriptReq,
                response = scriptResp,
                environment = environmentStore
            )

            when (scriptResult) {
                is ScriptExecutionResult.Success -> {
                    uiTestResults = scriptResult.testResults.map {
                        TestResult(
                            name = it.name,
                            passed = it.passed,
                            errorMessage = if (it.passed) null else it.errorMessage
                        )
                    }
                    uiConsoleLogs.addAll(scriptResult.logs)
                }

                is ScriptExecutionResult.Error -> {
                    uiTestResults = listOf(
                        TestResult(
                            name = "Script Execution Error",
                            passed = false,
                            errorMessage = scriptResult.message
                        )
                    )
                    uiConsoleLogs.add("[Test Script Error] ${scriptResult.message}")
                }
            }
        }

        ScriptedExecutionResult(
            result = res,
            formattedBody = bodyText,
            mimeType = mime,
            testResults = uiTestResults,
            consoleLogs = uiConsoleLogs
        )
    }
}
