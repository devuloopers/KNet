package com.devuloopers.knet.domain.collection.runner

import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.model.TestAssertionResult
import com.devuloopers.knet.domain.collection.usecase.ExecutionResult
import com.devuloopers.knet.domain.scripting.model.ScriptLanguage
import com.devuloopers.knet.domain.workspace.model.EnvironmentStore

/**
 * Result data class for an individual request test assertion evaluation within a batch runner suite.
 */
data class SuiteRequestResult(
    val request: SavedApiRequest,
    val executionResult: ExecutionResult,
    val assertionResults: List<TestAssertionResult>
)

/**
 * Overall summary data class for a batch collection run suite.
 */
data class SuiteRunSummary(
    val totalRequests: Int,
    val passedRequests: Int,
    val failedRequests: Int,
    val totalAssertions: Int,
    val passedAssertions: Int,
    val failedAssertions: Int,
    val totalDurationMs: Long,
    val requestResults: List<SuiteRequestResult>
)

/**
 * Domain test engine that evaluates assertions on API execution results.
 */
class CollectionTestRunner {

    /**
     * Evaluates standard test assertions for an executed request.
     */
    suspend fun evaluateAssertions(
        request: SavedApiRequest,
        result: ExecutionResult,
        testScript: String = request.scripts.test,
        scriptLanguage: ScriptLanguage = request.scripts.language,
        environmentStore: EnvironmentStore? = null
    ): List<TestAssertionResult> {
        val assertions = mutableListOf<TestAssertionResult>()

        // Default Status Code 2xx Assertion
        assertions.add(
            TestAssertionResult(
                id = "status_2xx",
                name = "Status code is 2xx",
                passed = result.statusCode in 200..299
            )
        )

        // Response Time Threshold Assertion
        assertions.add(
            TestAssertionResult(
                id = "latency_under_2000",
                name = "Response time is less than 2000ms",
                passed = result.latencyMs < 2000L
            )
        )

        return assertions
    }
}
