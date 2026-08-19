package com.devuloopers.knet.ui.desktop.apistudio.usecase

import com.devuloopers.knet.application.usecase.traffic.RecordHttpExchangeUseCase
import com.devuloopers.knet.application.port.script.ScriptExecutionCommand
import com.devuloopers.knet.application.port.script.ScriptExecutionOutcome
import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.port.script.ScriptRequest
import com.devuloopers.knet.application.port.script.ScriptResponse
import com.devuloopers.knet.application.port.script.UnavailableScriptExecutionPort
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.MimeType
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.model.RequestFormField
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.scripting.model.ScriptAssertion
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.httppanel.model.AuthType
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Result data DTO returned after scripted API execution.
 *
 * @property result Raw low-level execution result.
 * @property formattedBody Formatted response payload string.
 * @property mimeType Detected MIME type.
 * @property testResults List of post-response script assertion test results.
 * @property consoleLogs List of console logs captured during pre and post script execution.
 */
data class ScriptedExecutionResult(
    val result: ExecutionResult,
    val formattedBody: String,
    val mimeType: MimeType,
    val testResults: List<ScriptAssertion>,
    val consoleLogs: List<String>
)

/**
 * Presentation-layer UseCase orchestrating pre-request script execution, network call dispatching,
 * response formatting, and post-response test script assertion evaluation.
 *
 * @property executeUseCase Low-level HTTP client request execution UseCase.
 * @property formatResponseBodyUseCase Response body formatting UseCase.
 * @property recordHttpExchangeUseCase Canonical recorder for direct executions; optional only for isolated tests.
 * @property ioDispatcher Coroutine dispatcher for background network/script I/O.
 */
class ExecuteScriptedApiRequestUseCase(
    private val executeUseCase: ExecuteClientApiRequestUseCase,
    private val formatResponseBodyUseCase: FormatResponseBodyUseCase,
    private val recordHttpExchangeUseCase: RecordHttpExchangeUseCase? = null,
    private val scriptExecutionPort: ScriptExecutionPort = UnavailableScriptExecutionPort,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Executes an API request with optional pre-request scripts, network call execution, and post-response test assertions.
     *
     * @param editorState Current active request editor configuration state.
     * @param proxyPort Active proxy port, or null if direct connection.
     * @return Formatted [ScriptedExecutionResult].
     */
    suspend fun execute(
        editorState: RequestEditorState,
        proxyPort: Int?
    ): ScriptedExecutionResult = withContext(ioDispatcher) {
        var effectiveUrl = editorState.url
        var headerMap = editorState.headers.sanitizeTransportHeaders().toMap()
        var queryParameters = editorState.queryParams
        var effectiveBody = editorState.bodyPayload

        var environment = emptyMap<String, String>()
        val uiConsoleLogs = mutableListOf<String>()

        val targetLanguage = editorState.scriptLanguage

        // 1. Pre-request Script Execution
        if (editorState.preRequestScript.isNotBlank()) {
            val scriptReq = ScriptRequest(
                url = effectiveUrl,
                method = editorState.method,
                headers = headerMap,
                queryParameters = queryParameters.toMap(),
                body = effectiveBody
            )
            val preScriptResult = scriptExecutionPort.execute(ScriptExecutionCommand(
                language = targetLanguage,
                source = editorState.preRequestScript,
                request = scriptReq,
                response = null,
                environment = environment,
            ))
            when (preScriptResult) {
                is ScriptExecutionOutcome.Success -> {
                    uiConsoleLogs.addAll(preScriptResult.logs)
                    effectiveUrl = preScriptResult.request.url
                    headerMap = preScriptResult.request.headers.toMap()
                    queryParameters = preScriptResult.request.queryParameters.toList()
                    effectiveBody = preScriptResult.request.body
                    environment = preScriptResult.environment
                }

                is ScriptExecutionOutcome.Failure -> {
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

        val httpMethod = HttpMethod.fromToken(editorState.method)

        val outboundBody = editorState.toOutboundBody(effectiveBody)

        val cookieMap = editorState.cookies.toMap()

        // 3. Network Execution
        val requestStartedAt = Clock.System.now().toEpochMilliseconds()
        val res = executeUseCase(
            url = effectiveUrl,
            method = httpMethod,
            headers = headerMap,
            queryParams = queryParameters,
            cookies = cookieMap,
            body = outboundBody,
            auth = authConfig,
            proxyPort = proxyPort
        )
        val requestCompletedAt = Clock.System.now().toEpochMilliseconds()

        if (proxyPort == null) {
            recordDirectExecution(
                effectiveUrl = appendAuthQuery(
                    appendQueryParameters(effectiveUrl, queryParameters),
                    authConfig,
                ),
                method = httpMethod,
                headers = requestHeaders(headerMap, cookieMap, authConfig),
                requestBody = outboundBody.recordableText(),
                result = res,
                startedAtEpochMillis = requestStartedAt,
                completedAtEpochMillis = requestCompletedAt,
            )
        }

        val mime = com.devuloopers.knet.domain.util.MimeTypeUtils.extractFromHeaders(res.headers)
        val bodyText = formatResponseBodyUseCase.execute(
            rawBody = res.responseBody,
            mimeType = mime
        )

        // 4. Post-response Test Script Execution
        var uiTestResults = emptyList<ScriptAssertion>()

        if (editorState.testScript.isNotBlank() && res.failureReason == null) {
            val scriptReq = ScriptRequest(
                url = editorState.url,
                method = editorState.method,
                headers = headerMap,
                queryParameters = queryParameters.toMap(),
                body = editorState.bodyPayload
            )
            val scriptResp = ScriptResponse(
                statusCode = res.statusCode,
                statusText = res.statusText,
                latencyMillis = res.latencyMs,
                responseSizeBytes = res.responseSizeBytes,
                headers = res.headers,
                body = res.responseBody
            )
            val scriptResult = scriptExecutionPort.execute(ScriptExecutionCommand(
                language = targetLanguage,
                source = editorState.testScript,
                request = scriptReq,
                response = scriptResp,
                environment = environment,
            ))

            when (scriptResult) {
                is ScriptExecutionOutcome.Success -> {
                    uiTestResults = scriptResult.assertions
                    uiConsoleLogs.addAll(scriptResult.logs)
                }

                is ScriptExecutionOutcome.Failure -> {
                    uiTestResults = listOf(
                        ScriptAssertion(
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

    /** Records direct execution as canonical traffic without making capture failure fail the request. */
    private suspend fun recordDirectExecution(
        effectiveUrl: String,
        method: HttpMethod,
        headers: List<Pair<String, String>>,
        requestBody: String?,
        result: ExecutionResult,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
    ) {
        val recorder = recordHttpExchangeUseCase ?: return
        try {
            recorder.execute(
                DirectTrafficCommandFactory.create(
                    exchangeId = com.devuloopers.knet.traffic.id.ExchangeId("api-studio-${Uuid.random()}"),
                    url = effectiveUrl,
                    method = method,
                    headers = headers,
                    requestBody = requestBody,
                    result = result,
                    startedAtEpochMillis = startedAtEpochMillis,
                    completedAtEpochMillis = completedAtEpochMillis,
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            KNetLogger.error(TAG, failure) { "Direct API Studio exchange could not be recorded canonically." }
        }
    }

    /** Mirrors the current execution use case's query assembly so recorded target metadata is exact. */
    private fun appendQueryParameters(url: String, queryParameters: List<Pair<String, String>>): String {
        if (queryParameters.isEmpty()) return url
        return com.devuloopers.knet.domain.util.UrlQueryStringParser.rebuildUrlWithQueryParams(
            baseUrl = url,
            queryParams = queryParameters,
        )
    }

    /** Mirrors request headers produced by the domain/client execution path. */
    private fun requestHeaders(
        headers: Map<String, String>,
        cookies: Map<String, String>,
        auth: ApiRequestAuth,
    ): List<Pair<String, String>> = buildList {
        addAll(headers.entries.map { (name, value) -> name to value })
        if (cookies.isNotEmpty() && none { (name, _) -> name.equals("Cookie", ignoreCase = true) }) {
            add("Cookie" to cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
        }
        when (auth) {
            is ApiRequestAuth.Bearer -> add("Authorization" to bearerValue(auth.token))
            is ApiRequestAuth.Basic -> {
                val value = kotlin.io.encoding.Base64.encode("${auth.username}:${auth.password}".encodeToByteArray())
                add("Authorization" to "Basic $value")
            }
            is ApiRequestAuth.ApiKey -> if (!auth.location.contains("query", ignoreCase = true)) {
                add(auth.name to auth.value)
            }
            is ApiRequestAuth.OAuth2 -> add(
                "Authorization" to "${auth.headerPrefix} ${auth.token}".trim(),
            )
            ApiRequestAuth.None -> Unit
            else -> Unit
        }
    }

    /** Normalizes a bearer token exactly as the current HTTP client does. */
    private fun bearerValue(token: String): String =
        if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"

    private fun RequestEditorState.toOutboundBody(effectiveText: String): OutboundRequestBody =
        when (bodyState.mode) {
            RequestBodyMode.NONE -> OutboundRequestBody.None
            RequestBodyMode.JSON -> OutboundRequestBody.Json(effectiveText)
            RequestBodyMode.RAW -> OutboundRequestBody.Text(
                content = effectiveText,
                mediaType = bodyState.rawSubFormat.contentType,
            )
            RequestBodyMode.GRAPHQL -> OutboundRequestBody.GraphQl(effectiveText)
            RequestBodyMode.FORM_DATA -> OutboundRequestBody.Multipart(
                bodyState.formDataEntries.toRequestFormFields(),
            )
            RequestBodyMode.X_WWW_FORM_URLENCODED -> OutboundRequestBody.FormUrlEncoded(
                bodyState.urlEncodedEntries.toRequestFormFields(),
            )
        }

    private fun List<com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry>.toRequestFormFields():
        List<RequestFormField> = asSequence()
        .filter { it.enabled && it.key.isNotBlank() }
        .map { RequestFormField(name = it.key, value = it.value) }
        .toList()

    private fun OutboundRequestBody.recordableText(): String? = when (this) {
        OutboundRequestBody.None -> null
        is OutboundRequestBody.Json -> content
        is OutboundRequestBody.Xml -> content
        is OutboundRequestBody.Text -> content
        is OutboundRequestBody.GraphQl -> content
        is OutboundRequestBody.FormUrlEncoded -> fields.joinToString("&") { "${it.name}=${it.value}" }
        is OutboundRequestBody.Multipart -> fields.joinToString("\n") { "${it.name}=${it.value}" }
    }

    private fun appendAuthQuery(url: String, auth: ApiRequestAuth): String {
        if (auth !is ApiRequestAuth.ApiKey || !auth.location.contains("query", ignoreCase = true)) return url
        if (auth.name.isBlank() || auth.value.isBlank()) return url
        val separator = if ('?' in url) '&' else '?'
        return "$url$separator${auth.name}=${auth.value}"
    }

    private companion object {
        private const val TAG = "ApiStudioDirectCapture"
    }
}
