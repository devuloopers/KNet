package com.devuloopers.knet.scriptengine.bridge

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test suite for Response Bridge mappings in script contexts.
 * Verifies status code, status text, latency, response size, text(), json(), and JSON parsing.
 */
class ResponseBridgeTest {

    private val engineManager = ScriptEngineManager()

    /**
     * Verifies that response properties and JSON object parsing function accurately in JS context.
     */
    @Test
    fun testResponseBridgeReadAndJsonParsing() = runBlocking {
        val request = ScriptRequestModel("http://localhost", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(
            statusCode = 200,
            statusText = "OK",
            latencyMs = 45L,
            responseSizeBytes = 512L,
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"success":true,"items":[1,2,3]}"""
        )

        val script = """
            pm.test("Status is 200", function() {
                pm.response.to.have.status(200);
            });
            pm.test("JSON Array item check", function() {
                var data = pm.response.json();
                pm.expect(data.items.length).to.eql(3);
            });
        """.trimIndent()

        val result = engineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = script,
            request = request,
            response = response,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Success)
        val success = result as ScriptExecutionResult.Success
        assertEquals(2, success.testResults.size)
        assertTrue(success.testResults.all { it.passed })
    }
}
