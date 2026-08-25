package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionCommand
import com.devuloopers.knet.application.port.script.ScriptExecutionOutcome
import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.port.script.ScriptRequest
import com.devuloopers.knet.application.port.script.ScriptResponse
import com.devuloopers.knet.domain.clientNetwork.model.ExecutionResult
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionBodyChunk
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionEvent
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutionResponseHead
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

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

/** Ordered API Studio HTTP execution events, retaining the existing terminal result contract. */
public sealed interface ApiStudioHttpExecutionEvent {
    public data class ResponseHead(public val value: HttpExecutionResponseHead) : ApiStudioHttpExecutionEvent
    public data class BodyChunk(public val value: HttpExecutionBodyChunk) : ApiStudioHttpExecutionEvent
    public data class Completed(public val value: ApiStudioExecutionResult) : ApiStudioHttpExecutionEvent
}

/**
 * Executes a canonical authored request with optional scripts.
 *
 * Compose state and editor widgets are intentionally excluded. Any future desktop, CLI, automation, or
 * companion-facing API Studio surface can invoke this workflow using the same [SavedApiRequest] document.
 *
 * @param executeRequest Executes the HTTP request through the outbound transport boundary.
 * @param formatResponseBody Formats the response according to its detected MIME type.
 * @param scriptExecution Executes supported pre-request and response-test scripts.
 * @param ioDispatcher Dispatcher used for network, script, and formatting work.
 */
public class ExecuteApiStudioRequestUseCase(
    private val executeRequest: ExecuteClientApiRequestUseCase,
    private val formatResponseBody: FormatResponseBodyUseCase,
    private val scriptExecution: ScriptExecutionPort,
    private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Executes [request] and returns its formatted result.
     *
     * @param request Complete authored request document.
     * @param proxyPort Active local proxy port, or null for direct transport. Direct execution is
     * not persisted as captured Traffic; only the proxy capture pipeline owns that responsibility.
     */
    public suspend fun execute(
        request: SavedApiRequest,
        proxyPort: Int?
    ): ApiStudioExecutionResult = withContext(ioDispatcher) {
        val prepared = prepare(request)
        val result = executeRequest(
            url = prepared.url,
            method = request.method,
            headers = prepared.headers,
            queryParams = prepared.queryParameters,
            cookies = prepared.cookies,
            body = prepared.outboundBody,
            auth = request.auth,
            proxyPort = proxyPort,
            httpVersionPreference = request.httpVersionPreference,
        )
        complete(request, prepared, result)
    }

    /**
     * Executes [request] as an ordered stream while preserving the same pre/post script pipeline.
     * Ordinary HTTP executors remain compatible through the domain terminal-event fallback.
     */
    public fun executeStreaming(
        request: SavedApiRequest,
        proxyPort: Int?,
    ): Flow<ApiStudioHttpExecutionEvent> = flow {
        val prepared = prepare(request)
        executeRequest.stream(
            url = prepared.url,
            method = request.method,
            headers = prepared.headers,
            queryParams = prepared.queryParameters,
            cookies = prepared.cookies,
            body = prepared.outboundBody,
            auth = request.auth,
            proxyPort = proxyPort,
            httpVersionPreference = request.httpVersionPreference,
        ).collect { event ->
            when (event) {
                is HttpExecutionEvent.ResponseHead -> emit(ApiStudioHttpExecutionEvent.ResponseHead(event.value))
                is HttpExecutionEvent.BodyChunk -> emit(ApiStudioHttpExecutionEvent.BodyChunk(event.value))
                is HttpExecutionEvent.Completed -> emit(
                    ApiStudioHttpExecutionEvent.Completed(complete(request, prepared, event.result)),
                )
            }
        }
    }.flowOn(ioDispatcher)

    private suspend fun prepare(request: SavedApiRequest): PreparedApiStudioExecution {
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
                        body = effectiveBody,
                    ),
                    response = null,
                    environment = environment,
                ),
            )) {
                is ScriptExecutionOutcome.Success -> {
                    consoleLogs.addAll(outcome.logs)
                    effectiveUrl = outcome.request.url
                    headerMap = outcome.request.headers.toMap()
                    queryParameters = outcome.request.queryParameters.toList()
                    effectiveBody = outcome.request.body
                    environment = outcome.environment
                }
                is ScriptExecutionOutcome.Failure -> consoleLogs += "[Pre-request Error] ${outcome.message}"
            }
        }
        return PreparedApiStudioExecution(
            url = effectiveUrl,
            headers = headerMap,
            queryParameters = queryParameters,
            cookies = request.cookies.asSequence()
                .filter { it.isEnabled }
                .associate { it.name to it.value },
            effectiveBody = effectiveBody,
            outboundBody = request.body.toOutboundBody(effectiveBody),
            environment = environment,
            consoleLogs = consoleLogs,
        )
    }

    private suspend fun complete(
        request: SavedApiRequest,
        prepared: PreparedApiStudioExecution,
        result: ExecutionResult,
    ): ApiStudioExecutionResult {

        val mimeType = MimeTypeUtils.extractFromHeaders(result.headers)
        val formattedBody = formatResponseBody.execute(result.responseBody, mimeType)
        val assertions = mutableListOf<ScriptAssertion>()
        val consoleLogs = prepared.consoleLogs.toMutableList()

        if (request.scripts.test.isNotBlank() && result.failureReason == null) {
            when (val outcome = scriptExecution.execute(
                ScriptExecutionCommand(
                    language = request.scripts.language,
                    source = request.scripts.test,
                    request = ScriptRequest(
                        url = prepared.url,
                        method = request.method.token,
                        headers = prepared.headers,
                        queryParameters = prepared.queryParameters.toMap(),
                        body = prepared.effectiveBody,
                    ),
                    response = ScriptResponse(
                        statusCode = result.statusCode,
                        statusText = result.statusText,
                        latencyMillis = result.latencyMs,
                        responseSizeBytes = result.responseSizeBytes,
                        headers = result.headers,
                        body = result.responseBody
                    ),
                    environment = prepared.environment,
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

        return ApiStudioExecutionResult(
            result = result,
            formattedBody = formattedBody,
            mimeType = mimeType,
            testResults = assertions,
            consoleLogs = consoleLogs
        )
    }

    private data class PreparedApiStudioExecution(
        val url: String,
        val headers: Map<String, String>,
        val queryParameters: List<Pair<String, String>>,
        val cookies: Map<String, String>,
        val effectiveBody: String,
        val outboundBody: OutboundRequestBody,
        val environment: Map<String, String>,
        val consoleLogs: List<String>,
    )

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

}
