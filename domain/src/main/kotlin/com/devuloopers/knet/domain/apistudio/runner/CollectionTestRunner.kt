package com.devuloopers.knet.domain.apistudio.runner

import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult

/**
 * Result data class for an individual request test assertion evaluation within a batch runner suite.
 */
data class SuiteRequestResult(
    val request: SavedApiRequest,
    val executionResult: ExecutionResult,
    val assertionResults: List<TestAssertionResult>
)

/**
 * Summary data class returned after running a collection batch test suite.
 */
data class SuiteRunSummary(
    val totalRequests: Int,
    val passedCount: Int,
    val failedCount: Int,
    val averageLatencyMs: Long,
    val results: List<SuiteRequestResult>
)

/**
 * Domain test engine that evaluates assertions on API execution results.
 */
class CollectionTestRunner {

    /**
     * Evaluates standard test assertions for an executed request.
     */
    fun evaluateAssertions(
        request: SavedApiRequest,
        result: ExecutionResult
    ): List<TestAssertionResult> {
        val assertions = mutableListOf<TestAssertionResult>()

        // 1. Status Code assertion
        val isStatusOk = result.statusCode == request.expectedStatus
        assertions.add(
            TestAssertionResult(
                id = "status_code_test",
                name = "Status code is ${request.expectedStatus}",
                passed = isStatusOk
            )
        )

        // 2. Response latency assertion (< 3000ms)
        val isLatencyOk = result.latencyMs < 3000L
        assertions.add(
            TestAssertionResult(
                id = "latency_test",
                name = "Response time < 3000 ms",
                passed = isLatencyOk
            )
        )

        // 3. Response body present assertion
        val isBodyOk = result.responseBody.isNotBlank()
        assertions.add(
            TestAssertionResult(
                id = "body_test",
                name = "Response body is not empty",
                passed = isBodyOk
            )
        )

        return assertions
    }
}
