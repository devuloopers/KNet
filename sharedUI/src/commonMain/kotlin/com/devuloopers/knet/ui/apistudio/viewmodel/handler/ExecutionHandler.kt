package com.devuloopers.knet.ui.apistudio.viewmodel.handler

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth
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

/**
 * Result data class for a single request execution run.
 */
data class SingleExecutionOutcome(
    val result: ExecutionResult,
    val testResults: List<TestAssertionResult>,
    val scriptError: String? = null
)

/**
 * Pure handler managing single request execution, pre-request script execution, test assertion evaluation, and collection suite batch runs.
 *
 * @param proxyPort Optional local proxy port.
 */
class ExecutionHandler(
    private val proxyPort: Int? = null,
    private val apiClient: KNetApiClient = KNetApiClient(proxyPort),
    private val testRunner: CollectionTestRunner = CollectionTestRunner()
) {

    /**
     * Executes a single API request, evaluating pre-request scripts, network dispatch, and test script assertions.
     */
    suspend fun executeSingleRequest(request: SavedApiRequest): SingleExecutionOutcome {
        var finalUrl = request.url
        val finalHeaders = request.headers.filter { it.isEnabled }.associate { it.key to it.value }.toMutableMap()

        // Apply Authentication Headers
        when (val auth = request.auth) {
            is ApiRequestAuth.Bearer -> if (auth.token.isNotBlank()) finalHeaders["Authorization"] = "Bearer ${auth.token}"
            is ApiRequestAuth.Basic -> {
                val encoded = java.util.Base64.getEncoder().encodeToString("${auth.username}:${auth.password}".toByteArray())
                finalHeaders["Authorization"] = "Basic $encoded"
            }
            is ApiRequestAuth.ApiKey -> if (auth.value.isNotBlank() && auth.location.equals("Header", ignoreCase = true)) {
                finalHeaders[auth.name.ifBlank { "X-API-Key" }] = auth.value
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

        // Execute Pre-request script
        if (request.scripts.preRequest.isNotBlank()) {
            val scriptReq = ScriptRequestModel(
                url = finalUrl,
                method = request.methodString,
                headers = finalHeaders,
                queryParams = mutableMapOf(),
                body = request.body.content
            )
            val scriptRuntime = ScriptRuntime()
            when (val preRes = scriptRuntime.executeScript(request.scripts.preRequest, request.scripts.language, scriptReq)) {
                is ScriptExecutionResult.Success -> {
                    finalUrl = preRes.request.url
                    finalHeaders.putAll(preRes.request.headers)
                }
                is ScriptExecutionResult.Error -> {
                    return SingleExecutionOutcome(
                        result = ExecutionResult(0, "Pre-request Script Error", emptyMap(), "", 0, 0, false, preRes.message),
                        testResults = emptyList(),
                        scriptError = "Pre-request Error: ${preRes.message}"
                    )
                }
            }
        }

        val networkResult = apiClient.execute(
            url = finalUrl,
            method = request.methodString,
            headers = finalHeaders,
            body = request.body.content,
            bodyType = RequestBodyType.JSON
        )

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
            headers = finalHeaders.map { (key, value) -> com.devuloopers.knet.domain.apistudio.model.RequestHeader(key, value) }
        )

        val testResults = testRunner.evaluateAssertions(
            request = mutatedRequest,
            result = domainResult,
            testScript = request.scripts.test,
            scriptLanguage = request.scripts.language
        )

        return SingleExecutionOutcome(
            result = domainResult,
            testResults = testResults
        )
    }

    /**
     * Executes an entire collection suite sequentially.
     */
    suspend fun executeCollectionSuite(collection: ApiCollection): SuiteRunSummary {
        val requests = collection.folders.flatMap { it.requests }
        val results = mutableListOf<SuiteRequestResult>()

        for (req in requests) {
            val outcome = executeSingleRequest(req)
            results.add(
                SuiteRequestResult(
                    request = req,
                    executionResult = outcome.result,
                    assertionResults = outcome.testResults
                )
            )
        }

        val total = results.size
        val passed = results.count { r -> r.assertionResults.all { it.passed } }
        val failed = total - passed
        val avgLatency = if (total > 0) results.map { it.executionResult.latencyMs }.average().toLong() else 0L

        return SuiteRunSummary(
            totalRequests = total,
            passedCount = passed,
            failedCount = failed,
            averageLatencyMs = avgLatency,
            results = results
        )
    }
}
