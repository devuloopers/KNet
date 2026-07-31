package com.devuloopers.knet.ui.apistudio.handler

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth
import com.devuloopers.knet.domain.apistudio.model.RequestHeader
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.domain.apistudio.runner.CollectionTestRunner
import com.devuloopers.knet.domain.apistudio.runner.SuiteRequestResult
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.engine.client.KNetApiClient
import com.devuloopers.knet.engine.client.model.RequestBodyType
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.runtime.ScriptRuntime
import com.devuloopers.knet.ui.apistudio.model.ResponsePresentation
import com.devuloopers.knet.ui.apistudio.model.ResponsePresentationBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Result data class for a single request execution run.
 *
 * @property result Main execution outcome containing HTTP status, body, latency, and headers.
 * @property testResults Evaluated test assertion results.
 * @property scriptError Error message if a pre-request script failed to execute.
 * @property presentation Pre-computed [ResponsePresentation] model built on background threads.
 */
data class SingleExecutionOutcome(
    val result: ExecutionResult,
    val testResults: List<TestAssertionResult>,
    val scriptError: String? = null,
    val presentation: ResponsePresentation? = null
)

/**
 * Execution pipeline owner managing single API request dispatching, pre-request script execution,
 * test script assertions, dispatcher routing, and collection suite batch runs.
 *
 * Enforces strict thread separation:
 * - [Dispatchers.Default] for CPU-intensive script parsing, GraalJS engine execution, and test assertion evaluation.
 * - [Dispatchers.IO] for outbound network requests and socket I/O operations via [KNetApiClient].
 * - Configurable minimum loading window to prevent visual flickering on ultra-fast endpoints without delaying network operations.
 *
 * @param proxyPort Optional local proxy port.
 * @param apiClient Outbound HTTP network client instance.
 * @param testRunner Test assertion evaluation engine.
 */
class ExecutionHandler(
    private val proxyPort: Int? = null,
    private val apiClient: KNetApiClient = KNetApiClient(proxyPort),
    private val testRunner: CollectionTestRunner = CollectionTestRunner()
) {
    private val suiteExecutor = CollectionSuiteExecutor(apiClient, testRunner)

    companion object {
        /** Minimum visual loading window in milliseconds to prevent single-frame visual flickering on ultra-fast responses. */
        const val MIN_LOADING_DURATION_MS: Long = 200L
    }

    /**
     * Normalizes a raw URL input by prepending "http://" if protocol prefix is missing.
     *
     * @param rawUrl Raw input URL string.
     * @return Normalized URL string with protocol.
     */
    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Executes a single API request, evaluating pre-request scripts, network dispatching, and test script assertions.
     * Enforces dispatcher routing and a minimum visual loading duration window.
     *
     * @param request The [SavedApiRequest] to execute.
     * @param enforceMinDuration If true, holds the execution return until at least [MIN_LOADING_DURATION_MS] has elapsed.
     * @return [SingleExecutionOutcome] containing response data, assertion results, and script errors.
     */
    suspend fun executeSingleRequest(
        request: SavedApiRequest,
        enforceMinDuration: Boolean = true
    ): SingleExecutionOutcome {
        val startTime = System.currentTimeMillis()

        val outcome = try {
            executeSingleRequestPipeline(request)
        } catch (e: Exception) {
            SingleExecutionOutcome(
                result = ExecutionResult(
                    statusCode = 0,
                    statusText = "Network Error",
                    headers = emptyMap(),
                    responseBody = "",
                    latencyMs = 0L,
                    responseSizeBytes = 0L,
                    isSuccess = false,
                    errorMessage = e.message ?: e.toString()
                ),
                testResults = emptyList()
            )
        }

        if (enforceMinDuration) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < MIN_LOADING_DURATION_MS) {
                delay(MIN_LOADING_DURATION_MS - elapsedTime)
            }
        }

        return outcome
    }

    /**
     * Internal execution pipeline performing authentication header injection, pre-request script evaluation
     * on [Dispatchers.Default], network dispatching on [Dispatchers.IO], and test script evaluation on [Dispatchers.Default].
     */
    private suspend fun executeSingleRequestPipeline(request: SavedApiRequest): SingleExecutionOutcome {
        var finalUrl = normalizeUrl(request.url)
        val finalHeaders = request.headers
            .filter { it.isEnabled && !it.value.startsWith("<") }
            .associate { it.key to it.value }
            .toMutableMap()

        // 1. Apply Authentication Headers
        when (val auth = request.auth) {
            is ApiRequestAuth.Bearer -> if (auth.token.isNotBlank()) finalHeaders["Authorization"] = "Bearer ${auth.token}"
            is ApiRequestAuth.Basic -> {
                if (auth.username.isNotBlank() || auth.password.isNotBlank()) {
                    @OptIn(ExperimentalEncodingApi::class)
                    val encoded = Base64.encode("${auth.username}:${auth.password}".encodeToByteArray())
                    finalHeaders["Authorization"] = "Basic $encoded"
                }
            }
            is ApiRequestAuth.ApiKey -> {
                val keyName = auth.name.ifBlank { "X-API-Key" }
                if (auth.value.isNotBlank()) {
                    if (auth.location.equals("Header", ignoreCase = true)) {
                        finalHeaders[keyName] = auth.value
                    } else {
                        val separator = if (finalUrl.contains("?")) "&" else "?"
                        finalUrl += "$separator$keyName=${auth.value}"
                    }
                }
            }
            is ApiRequestAuth.OAuth2 -> {
                val prefix = auth.headerPrefix.ifBlank { "Bearer" }
                if (auth.token.isNotBlank()) finalHeaders["Authorization"] = "$prefix ${auth.token}"
            }
            is ApiRequestAuth.AwsSignature -> if (auth.accessKey.isNotBlank()) {
                finalHeaders["Authorization"] = "AWS4-HMAC-SHA256 Credential=${auth.accessKey}/${auth.region}/${auth.service}/aws4_request"
            }
            else -> {}
        }

        // 2. Execute Pre-request Script on Dispatchers.Default (CPU-bound)
        if (request.scripts.preRequest.isNotBlank()) {
            val scriptOutcome = withContext(Dispatchers.Default) {
                val scriptReq = ScriptRequestModel(
                    url = finalUrl,
                    method = request.methodString,
                    headers = finalHeaders,
                    queryParams = mutableMapOf(),
                    body = request.body.content
                )
                val scriptRuntime = ScriptRuntime()
                scriptRuntime.executeScript(request.scripts.preRequest, request.scripts.language, scriptReq)
            }

            when (scriptOutcome) {
                is ScriptExecutionResult.Success -> {
                    finalUrl = scriptOutcome.request.url
                    finalHeaders.putAll(scriptOutcome.request.headers)
                }
                is ScriptExecutionResult.Error -> {
                    return SingleExecutionOutcome(
                        result = ExecutionResult(0, "Pre-request Script Error", emptyMap(), "", 0, 0, false, scriptOutcome.message),
                        testResults = emptyList(),
                        scriptError = "Pre-request Error: ${scriptOutcome.message}"
                    )
                }
            }
        }

        // 3. Execute HTTP Network Request on Dispatchers.IO (I/O-bound)
        val networkResult = withContext(Dispatchers.IO) {
            apiClient.execute(
                url = finalUrl,
                method = request.methodString,
                headers = finalHeaders,
                body = request.body.content,
                bodyType = RequestBodyType.JSON
            )
        }

        val domainResult = ExecutionResult(
            statusCode = networkResult.statusCode,
            statusText = networkResult.statusText,
            headers = networkResult.headers,
            responseBody = networkResult.responseBody,
            latencyMs = networkResult.latencyMs,
            responseSizeBytes = networkResult.responseSizeBytes,
            isSuccess = networkResult.isSuccess,
            errorMessage = networkResult.errorMessage
        )

        val mutatedRequest = request.copy(
            url = finalUrl,
            headers = finalHeaders.map { (key, value) -> RequestHeader(key, value) }
        )

        // 4. Evaluate Test Assertions on Dispatchers.Default (CPU-bound)
        val testResults = withContext(Dispatchers.Default) {
            testRunner.evaluateAssertions(
                request = mutatedRequest,
                result = domainResult,
                testScript = request.scripts.test,
                scriptLanguage = request.scripts.language
            )
        }

        // 5. Pre-compute ResponsePresentation UI Model on Dispatchers.Default (CPU-bound)
        val presentation = ResponsePresentationBuilder.build(
            headers = networkResult.headers,
            bodyText = networkResult.responseBody
        )

        return SingleExecutionOutcome(
            result = domainResult,
            testResults = testResults,
            presentation = presentation
        )
    }

    private val suitePlanner = SuiteExecutionPlanner()

    /**
     * Executes an entire collection suite sequentially off the UI thread via [CollectionSuiteExecutor].
     *
     * @param collection The [ApiCollection] containing requests to execute.
     * @return Summary of the suite execution run [SuiteRunSummary].
     */
    suspend fun executeCollectionSuite(collection: ApiCollection): SuiteRunSummary {
        return suiteExecutor.executeSuite(listOf(collection))
    }

    /**
     * Executes a list of collection suites asynchronously off the UI thread via [CollectionSuiteExecutor].
     *
     * @param collections Target list of [ApiCollection] instances.
     * @return Aggregated [SuiteRunSummary].
     */
    suspend fun executeCollectionSuite(collections: List<ApiCollection>): SuiteRunSummary {
        return suiteExecutor.executeSuite(collections)
    }

    /**
     * Resolves a target [SuiteExecutionScope] into an execution queue and executes it off the UI thread.
     *
     * @param scope Target [SuiteExecutionScope] specifying execution boundary.
     * @param collections Complete list of workspace [ApiCollection] instances.
     * @param currentRequest Optional focused request for [SuiteExecutionScope.CurrentRequest].
     * @return Summary of the suite execution run [SuiteRunSummary].
     */
    suspend fun executeSuiteScope(
        scope: SuiteExecutionScope,
        collections: List<ApiCollection>,
        currentRequest: SavedApiRequest? = null
    ): SuiteRunSummary {
        val targetQueue = suitePlanner.planExecutionQueue(scope, collections, currentRequest)
        return suiteExecutor.executeRequests(targetQueue)
    }
}
