package com.devuloopers.knet.ui.apistudio.handler

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.runner.CollectionTestRunner
import com.devuloopers.knet.domain.apistudio.runner.SuiteRequestResult
import com.devuloopers.knet.domain.apistudio.runner.SuiteRunSummary
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.engine.client.KNetApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated background executor pipeline for running API collection suites and request queues.
 * Enforces strict thread separation:
 * - [Dispatchers.IO] for outbound network requests via [KNetApiClient].
 * - [Dispatchers.Default] for CPU-intensive script assertion evaluation and summary calculations.
 *
 * @property apiClient Outbound network client for executing requests.
 * @property testRunner Test assertion runner for evaluating test scripts.
 */
class CollectionSuiteExecutor(
    private val apiClient: KNetApiClient,
    private val testRunner: CollectionTestRunner
) {

    /**
     * Asynchronously executes a target queue of [SavedApiRequest] instances off the UI thread.
     *
     * @param targetRequests List of requests to execute.
     * @return Immutable [SuiteRunSummary] aggregating execution outcomes, pass/fail counts, and average latency.
     */
    suspend fun executeRequests(targetRequests: List<SavedApiRequest>): SuiteRunSummary = withContext(Dispatchers.Default) {
        if (targetRequests.isEmpty()) {
            return@withContext SuiteRunSummary(
                totalRequests = 0,
                passedCount = 0,
                failedCount = 0,
                averageLatencyMs = 0L,
                results = emptyList()
            )
        }

        val resultsList = mutableListOf<SuiteRequestResult>()

        for (request in targetRequests) {
            val netResponse = withContext(Dispatchers.IO) {
                apiClient.execute(
                    url = request.url,
                    method = request.methodString,
                    headers = request.headers
                        .filter { it.isEnabled && !it.value.startsWith("<") }
                        .associate { it.key to it.value },
                    body = request.body.content
                )
            }

            val domainResult = ExecutionResult(
                statusCode = netResponse.statusCode,
                statusText = netResponse.statusText,
                headers = netResponse.headers,
                responseBody = netResponse.responseBody,
                latencyMs = netResponse.latencyMs,
                responseSizeBytes = netResponse.responseSizeBytes,
                isSuccess = netResponse.isSuccess,
                errorMessage = netResponse.errorMessage
            )

            val assertions = testRunner.evaluateAssertions(request, domainResult)
            resultsList.add(SuiteRequestResult(request, domainResult, assertions))
        }

        val passedCount = resultsList.count { result ->
            result.executionResult.isSuccess && result.assertionResults.all { assertion -> assertion.passed }
        }
        val failedCount = resultsList.size - passedCount
        val averageLatencyMs = if (resultsList.isNotEmpty()) {
            resultsList.map { it.executionResult.latencyMs }.average().toLong()
        } else 0L

        SuiteRunSummary(
            totalRequests = resultsList.size,
            passedCount = passedCount,
            failedCount = failedCount,
            averageLatencyMs = averageLatencyMs,
            results = resultsList
        )
    }

    /**
     * Convenience method executing all requests across the provided [collections].
     *
     * @param collections Target list of collections.
     * @return Aggregated [SuiteRunSummary].
     */
    suspend fun executeSuite(collections: List<ApiCollection>): SuiteRunSummary {
        val requests = collections.flatMap { collection ->
            collection.folders.flatMap { folder -> folder.requests }
        }
        return executeRequests(requests)
    }
}
