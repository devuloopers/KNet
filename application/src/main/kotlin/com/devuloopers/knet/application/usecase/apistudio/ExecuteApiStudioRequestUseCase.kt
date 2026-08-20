package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionCommand
import com.devuloopers.knet.application.port.script.ScriptExecutionOutcome
import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.port.script.ScriptRequest
import com.devuloopers.knet.application.port.script.ScriptResponse
import com.devuloopers.knet.application.usecase.traffic.RecordHttpExchangeUseCase
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.model.MimeType
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.clientNetwork.model.RequestFormField
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.usecase.ExecuteClientApiRequestUseCase
import com.devuloopers.knet.domain.clientNetwork.usecase.FormatResponseBodyUseCase
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.sanitizeTransportHeaders
import com.devuloopers.knet.domain.util.MimeTypeUtils
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.scripting.model.ScriptAssertion
import com.devuloopers.knet.traffic.id.ExchangeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Complete UI-neutral result of executing one authored API Studio request.
 *
 * @property result Canonical outbound execution result.
 * @property formattedBody Presentation-ready formatted response payload.
 * @property mimeType Detected response MIME type.
 * @property testResults Assertions emitted by the response script.
 * @property consoleLogs Ordered logs emitted by request and response scripts.
 */
public data class ApiStudioExecutionResult(
    public val result: ExecutionResult,
    public val formattedBody: String,
    public val mimeType: MimeType,
    public val testResults: List<ScriptAssertion>,
    public val consoleLogs: List<String>
)

/**
 * Executes a canonical authored request with optional scripts and canonical direct-traffic recording.
 *
 * Compose state and editor widgets are intentionally excluded. Any future desktop, CLI, automation, or
 * companion-facing API Studio surface can invoke this workflow using the same [SavedApiRequest] document.
 *
 * @param executeRequest Executes the HTTP request through the outbound transport boundary.
 * @param formatResponseBody Formats the response according to its detected MIME type.
 * @param recordHttpExchange Records direct requests in the canonical Traffic stream.
 * @param scriptExecution Executes supported pre-request and response-test scripts.
 * @param ioDispatcher Dispatcher used for network, script, formatting, and recording work.
 */
public class ExecuteApiStudioRequestUseCase(
    private val executeRequest: ExecuteClientApiRequestUseCase,
    private val formatResponseBody: FormatResponseBodyUseCase,
    private val recordHttpExchange: RecordHttpExchangeUseCase,
    private val scriptExecution: ScriptExecutionPort,
    private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Executes [request] and returns its formatted result.
     *
     * @param request Complete authored request document.
     * @param proxyPort Active local proxy port, or null for direct transport.
     */
    public suspend fun execute(
        request: SavedApiRequest,
        proxyPort: Int?
    ): ApiStudioExecutionResult = withContext(ioDispatcher) {
        var effectiveUrl = request.url
        var headerMap = request.headers.asSequence()
            .filter { it.isEnabled }
            .map { it.key to it.value }
            .toList()
            .sanitizeTransportHeaders()
            .toMap()
        var queryParameters = if (request.queryParameters.isNotEmpty()) {
            request.queryParameters.asSequence()
                .filter { it.isEnabled }
                .map { it.name to it.value }
                .toList()
        } else {
            UrlQueryStringParser.parseQueryParams(request.url)
        }
        var effectiveBody = request.body.content
        var environment = emptyMap<String, String>()
        val consoleLogs = mutableListOf<String>()

        if (request.scripts.preRequest.isNotBlank()) {
            when (val outcome = scriptExecution.execute(
                ScriptExecutionCommand(
                    language = request.scripts.language,
                    source = request.scripts.preRequest,
                    request = ScriptRequest(
                        url = effectiveUrl,
                        method = request.method.token,
                        headers = headerMap,
                        queryParameters = queryParameters.toMap(),
                        body = effectiveBody
                    ),
                    response = null,
                    environment = environment
                )
            )) {
                is ScriptExecutionOutcome.Success -> {
                    consoleLogs.addAll(outcome.logs)
                    effectiveUrl = outcome.request.url
                    headerMap = outcome.request.headers.toMap()
                    queryParameters = outcome.request.queryParameters.toList()
                    effectiveBody = outcome.request.body
                    environment = outcome.environment
                }
                is ScriptExecutionOutcome.Failure -> {
                    consoleLogs.add("[Pre-request Error] ${outcome.message}")
                }
            }
        }

        val cookies = request.cookies.asSequence()
            .filter { it.isEnabled }
            .associate { it.name to it.value }
        val outboundBody = request.body.toOutboundBody(effectiveBody)
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val result = executeRequest(
            url = effectiveUrl,
            method = request.method,
            headers = headerMap,
            queryParams = queryParameters,
            cookies = cookies,
            body = outboundBody,
            auth = request.auth,
            proxyPort = proxyPort
        )
        val completedAt = Clock.System.now().toEpochMilliseconds()

        if (proxyPort == null) {
            recordDirectExecution(
                effectiveUrl = appendAuthQuery(
                    appendQueryParameters(effectiveUrl, queryParameters),
                    request.auth
                ),
                request = request,
                headers = requestHeaders(headerMap, cookies, request.auth),
                requestBody = outboundBody.recordableText(),
                result = result,
                startedAtEpochMillis = startedAt,
                completedAtEpochMillis = completedAt
            )?.let { failureMessage ->
                consoleLogs += "[Traffic Recording Error] $failureMessage"
            }
        }

        val mimeType = MimeTypeUtils.extractFromHeaders(result.headers)
        val formattedBody = formatResponseBody.execute(result.responseBody, mimeType)
        val assertions = mutableListOf<ScriptAssertion>()

        if (request.scripts.test.isNotBlank() && result.failureReason == null) {
            when (val outcome = scriptExecution.execute(
                ScriptExecutionCommand(
                    language = request.scripts.language,
                    source = request.scripts.test,
                    request = ScriptRequest(
                        url = effectiveUrl,
                        method = request.method.token,
                        headers = headerMap,
                        queryParameters = queryParameters.toMap(),
                        body = effectiveBody
                    ),
                    response = ScriptResponse(
                        statusCode = result.statusCode,
                        statusText = result.statusText,
                        latencyMillis = result.latencyMs,
                        responseSizeBytes = result.responseSizeBytes,
                        headers = result.headers,
                        body = result.responseBody
                    ),
                    environment = environment
                )
            )) {
                is ScriptExecutionOutcome.Success -> {
                    assertions.addAll(outcome.assertions)
                    consoleLogs.addAll(outcome.logs)
                }
                is ScriptExecutionOutcome.Failure -> {
                    assertions += ScriptAssertion(
                        name = "Script Execution Error",
                        passed = false,
                        errorMessage = outcome.message
                    )
                    consoleLogs += "[Test Script Error] ${outcome.message}"
                }
            }
        }

        ApiStudioExecutionResult(
            result = result,
            formattedBody = formattedBody,
            mimeType = mimeType,
            testResults = assertions,
            consoleLogs = consoleLogs
        )
    }

    private suspend fun recordDirectExecution(
        effectiveUrl: String,
        request: SavedApiRequest,
        headers: List<Pair<String, String>>,
        requestBody: String?,
        result: ExecutionResult,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long
    ): String? {
        try {
            recordHttpExchange.execute(
                DirectTrafficCommandFactory.create(
                    exchangeId = ExchangeId("api-studio-${Uuid.random()}"),
                    url = effectiveUrl,
                    method = request.method,
                    headers = headers,
                    requestBody = requestBody,
                    result = result,
                    startedAtEpochMillis = startedAtEpochMillis,
                    completedAtEpochMillis = completedAtEpochMillis
                )
            )
            return null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            return failure.message ?: "Direct exchange could not be recorded."
        }
    }

    private fun appendQueryParameters(url: String, queryParameters: List<Pair<String, String>>): String =
        if (queryParameters.isEmpty()) url else UrlQueryStringParser.rebuildUrlWithQueryParams(url, queryParameters)

    private fun requestHeaders(
        headers: Map<String, String>,
        cookies: Map<String, String>,
        auth: ApiRequestAuth
    ): List<Pair<String, String>> = buildList {
        addAll(headers.entries.map { (name, value) -> name to value })
        if (cookies.isNotEmpty() && none { (name, _) -> name.equals("Cookie", ignoreCase = true) }) {
            add("Cookie" to cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
        }
        when (auth) {
            is ApiRequestAuth.Bearer -> add("Authorization" to bearerValue(auth.token))
            is ApiRequestAuth.Basic -> {
                val value = Base64.encode("${auth.username}:${auth.password}".encodeToByteArray())
                add("Authorization" to "Basic $value")
            }
            is ApiRequestAuth.ApiKey -> if (!auth.location.contains("query", ignoreCase = true)) {
                add(auth.name to auth.value)
            }
            is ApiRequestAuth.OAuth2 -> add(
                "Authorization" to "${auth.headerPrefix} ${auth.token}".trim()
            )
            is ApiRequestAuth.AwsSignature,
            ApiRequestAuth.Inherit,
            ApiRequestAuth.None -> Unit
        }
    }

    private fun bearerValue(token: String): String =
        if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"

    private fun ApiRequestBody.toOutboundBody(effectiveText: String): OutboundRequestBody =
        when (type) {
            RequestBodyType.JSON -> OutboundRequestBody.Json(effectiveText)
            RequestBodyType.XML -> OutboundRequestBody.Xml(effectiveText)
            RequestBodyType.GRAPHQL -> OutboundRequestBody.GraphQl(effectiveText)
            RequestBodyType.FORM_DATA,
            RequestBodyType.MULTIPART -> OutboundRequestBody.Multipart(
                formDataFields.toRequestFormFields()
            )
            RequestBodyType.X_WWW_FORM_URLENCODED -> OutboundRequestBody.FormUrlEncoded(
                urlEncodedFields.toRequestFormFields()
            )
            RequestBodyType.RAW_TEXT -> OutboundRequestBody.Text(effectiveText, rawFormat.mediaType)
            RequestBodyType.NONE -> OutboundRequestBody.None
        }

    private fun List<com.devuloopers.knet.domain.collection.model.ApiRequestBodyField>.toRequestFormFields():
        List<RequestFormField> = asSequence()
        .filter { it.isEnabled && it.key.isNotBlank() }
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
}
