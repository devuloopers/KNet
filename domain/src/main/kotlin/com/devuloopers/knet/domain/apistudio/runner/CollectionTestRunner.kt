package com.devuloopers.knet.domain.apistudio.runner

import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.runtime.ScriptRuntime

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

    private val scriptRuntime = ScriptRuntime()

    /**
     * Evaluates standard test assertions and executes user test scripts for an executed request.
     */
    fun evaluateAssertions(
        request: SavedApiRequest,
        result: ExecutionResult,
        testScript: String = "",
        scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT
    ): List<TestAssertionResult> {
        val assertions = mutableListOf<TestAssertionResult>()

        // 1. Standard Status Code assertion
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

        // 4. Run User Test Scripts via ScriptRuntime
        if (testScript.isNotBlank()) {
            val scriptReq = ScriptRequestModel(
                url = request.url,
                method = request.methodString,
                headers = request.headers.associate { it.key to it.value }.toMutableMap(),
                queryParams = mutableMapOf(),
                body = request.body
            )
            val scriptResp = ScriptResponseModel(
                statusCode = result.statusCode,
                statusText = result.statusText,
                latencyMs = result.latencyMs,
                responseSizeBytes = result.responseSizeBytes,
                headers = result.headers,
                body = result.responseBody
            )

            when (val scriptRes = scriptRuntime.executeScript(testScript, scriptLanguage, scriptReq, scriptResp)) {
                is ScriptExecutionResult.Success -> {
                    scriptRes.testResults.forEachIndexed { index, tr ->
                        assertions.add(
                            TestAssertionResult(
                                id = "script_test_$index",
                                name = tr.name,
                                passed = tr.passed
                            )
                        )
                    }
                }
                is ScriptExecutionResult.Error -> {
                    assertions.add(
                        TestAssertionResult(
                            id = "script_error",
                            name = "Script Error: ${scriptRes.message}",
                            passed = false
                        )
                    )
                }
            }
        }

        return assertions
    }
}

