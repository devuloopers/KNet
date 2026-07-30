package com.devuloopers.knet.scriptengine

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import com.devuloopers.knet.scriptengine.javascript.GraalJsScriptEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for multi-language script execution platform verifying GraalJS engine and Postman compatibility API.
 */
class JsScriptEngineTest {

    private val engineManager = ScriptEngineManager()

    /**
     * Verifies that JavaScript test assertions (pm.test, pm.expect, pm.response) execute successfully via ScriptEngineManager.
     */
    @Test
    fun testJsScriptExecutionSuccess() = runBlocking {
        val request = ScriptRequestModel(
            url = "http://localhost:9090/api/test/auth/bearer",
            method = "GET",
            headers = mutableMapOf("Authorization" to "Bearer secret_token"),
            queryParams = mutableMapOf(),
            body = ""
        )
        val response = ScriptResponseModel(
            statusCode = 200,
            statusText = "OK",
            latencyMs = 120,
            responseSizeBytes = 235,
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"status":200,"message":"Success","data":{"authenticated":true}}"""
        )

        val script = """
            console.log("Starting test script execution");
            pm.test("Status code is 200", function () {
                pm.response.to.have.status(200);
            });
            pm.test("Response is authenticated", function () {
                var json = pm.response.json();
                pm.expect(json.data.authenticated).to.eql(true);
            });
            pm.environment.set("auth_status", "VALID");
        """.trimIndent()

        val environmentStore = EnvironmentStore()

        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = script,
            request = request,
            response = response,
            environment = environmentStore
        )

        if (result is ScriptExecutionResult.Error) {
            fail("Script execution failed with error: ${result.message}")
        }

        val successResult = result as ScriptExecutionResult.Success
        assertEquals(2, successResult.testResults.size)
        val failedTests = successResult.testResults.filter { !it.passed }
        assertTrue(
            failedTests.isEmpty(),
            "All tests should pass. Failures: ${failedTests.map { "${it.name}: ${it.errorMessage}" }}"
        )
        assertEquals("VALID", successResult.environmentUpdates["auth_status"])
        assertTrue(successResult.logs.isNotEmpty(), "Logs should be captured")
    }
}
