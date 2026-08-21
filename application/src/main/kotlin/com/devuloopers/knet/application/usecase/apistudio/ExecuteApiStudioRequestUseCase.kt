package com.devuloopers.knet.application.usecase.apistudio

import com.devuloopers.knet.application.port.script.ScriptExecutionCommand
import com.devuloopers.knet.application.port.script.ScriptExecutionOutcome
import com.devuloopers.knet.application.port.script.ScriptExecutionPort
import com.devuloopers.knet.application.port.script.ScriptRequest
import com.devuloopers.knet.application.port.script.ScriptResponse
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
import kotlinx.coroutines.CoroutineDispatcher
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
        val result = executeRequest(
            url = effectiveUrl,
            method = request.method,
            headers = headerMap,
            queryParams = queryParameters,
            cookies = cookies,
            body = outboundBody,
            auth = request.auth,
            proxyPort = proxyPort,
            httpVersionPreference = request.httpVersionPreference,
        )

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
