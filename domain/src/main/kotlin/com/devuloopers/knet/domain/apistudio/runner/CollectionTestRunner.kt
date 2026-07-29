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
        testScript: String = request.scripts.test,
        scriptLanguage: ScriptLanguage = request.scripts.language
    ): List<TestAssertionResult> {
        val assertions = mutableListOf<TestAssertionResult>()

        // Run User-Defined Test Scripts via ScriptRuntime
        if (testScript.isNotBlank()) {
            val scriptReq = ScriptRequestModel(
                url = request.url,
                method = request.methodString,
                headers = request.headers.associate { it.key to it.value }.toMutableMap(),
                queryParams = mutableMapOf(),
                body = request.body.content
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

