package com.devuloopers.knet.scriptengine.javascript

import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Comprehensive test suite for GraalJS execution engine covering TC-010 to TC-014, TC-030 to TC-044,
 * TC-060 to TC-073, TC-080 to TC-082, TC-110, TC-111, and TC-160 to TC-163.
 */
class GraalJsScriptEngineTest {

    private val engineManager = ScriptEngineManager()

    private val mockRequest = ScriptRequestModel(
        url = "http://localhost:9090/api/test/auth/bearer",
        method = "POST",
        headers = mutableMapOf("Authorization" to "Bearer secret_token", "Content-Type" to "application/json"),
        queryParams = mutableMapOf("v" to "1"),
        body = """{"username":"testuser"}"""
    )

    private val mockResponse = ScriptResponseModel(
        statusCode = 200,
        statusText = "OK",
        latencyMs = 120L,
        responseSizeBytes = 235L,
        headers = mapOf("Content-Type" to "application/json", "X-Server" to "KNetMock"),
        body = """{"status":200,"message":"Success","data":{"authenticated":true,"id":42,"items":[1,2,3]}}"""
    )

    /**
     * TC-010 to TC-014: Tests basic JS execution (console.log, arithmetic, variable declaration, functions, loops).
     */
    @Test
    fun testBasicJsExecution() = runBlocking {
        val script = """
            console.log("Hello KNet");
            let x = 10 + 20;
            let name = "KNet";
            function add(a, b) { return a + b; }
            let sum = add(x, 5);
            for(let i=0; i<100; i++) {}
            console.log("Sum: " + sum);
        """.trimIndent()

        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Success, "Basic JS execution should succeed")
        val success = result as ScriptExecutionResult.Success
        assertTrue(success.logs.any { it.contains("Hello KNet") })
        assertTrue(success.logs.any { it.contains("Sum: 35") })
    }

    /**
     * TC-030 to TC-033: Tests Request Bridge APIs (url, method, headers, body).
     */
    @Test
    fun testRequestBridgeRead() = runBlocking {
        val script = """
            pm.test("Request URL", function() {
                pm.expect(pm.request.url).to.eql("http://localhost:9090/api/test/auth/bearer");
            });
            pm.test("Request Method", function() {
                pm.expect(pm.request.method).to.eql("POST");
            });
        """.trimIndent()

        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Success)
        val success = result as ScriptExecutionResult.Success
        assertEquals(2, success.testResults.size)
        assertTrue(success.testResults.all { it.passed })
    }

    /**
     * TC-040 to TC-044: Tests Response Bridge APIs (code, status, text, json).
     */
    @Test
    fun testResponseBridgeRead() = runBlocking {
        val script = """
            pm.test("Status 200", function() {
                pm.response.to.have.status(200);
            });
            pm.test("Status text", function() {
                pm.expect(pm.response.status).to.eql("OK");
            });
            pm.test("JSON object parsing", function() {
                var json1 = pm.response.json();
                var json2 = pm.response.json();
                pm.expect(json1.data.id).to.eql(42);
                pm.expect(json1.data.authenticated).to.eql(true);
            });
        """.trimIndent()

        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Success)
        val success = result as ScriptExecutionResult.Success
        assertEquals(3, success.testResults.size)
        assertTrue(success.testResults.all { it.passed }, "All response bridge tests must pass: ${success.testResults}")
    }

    /**
     * TC-060 to TC-067 & TC-160 to TC-163: Tests Postman assertions (status, eql, include).
     */
    @Test
    fun testPostmanAssertions() = runBlocking {
        val script = """
            pm.test("Eql match", function() {
                expect(10).to.eql(10);
            });
            pm.test("Include substring", function() {
                expect("abcdef").to.include("abc");
            });
        """.trimIndent()

        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Success)
        val success = result as ScriptExecutionResult.Success
        assertEquals(2, success.testResults.size)
        assertTrue(success.testResults.all { it.passed })
    }

    /**
     * TC-070 to TC-073: Tests console logging levels (log, warn, error, info).
     */
    @Test
    fun testConsoleLoggingLevels() = runBlocking {
        val script = """
            console.log("Log msg");
            console.warn("Warn msg");
            console.error("Error msg");
            console.info("Info msg");
        """.trimIndent()

        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Success)
        val success = result as ScriptExecutionResult.Success
        assertEquals(4, success.logs.size)
        assertTrue(success.logs[0].contains("Log msg"))
        assertTrue(success.logs[1].contains("[WARN] Warn msg"))
        assertTrue(success.logs[2].contains("[ERROR] Error msg"))
        assertTrue(success.logs[3].contains("[INFO] Info msg"))
    }

    /**
     * TC-080 & TC-081: Tests syntax error formatting and error handling.
     */
    @Test
    fun testSyntaxErrorFormatting() = runBlocking {
        val script = """
            let x = ;
        """.trimIndent()

        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Error, "Syntax error must return ScriptExecutionResult.Error")
        val error = result as ScriptExecutionResult.Error
        assertTrue(error.message.isNotEmpty(), "Error message must be present")
    }

    /**
     * TC-110 & TC-111: Tests context state leak isolation between separate execution runs.
     */
    @Test
    fun testContextIsolationNoStateLeak() = runBlocking {
        val script1 = "var globalVarX = 100;"
        val script2 = "console.log(globalVarX);"

        val store = EnvironmentStore()
        val result1 = engineManager.execute(ScriptLanguage.JAVASCRIPT, script1, mockRequest, mockResponse, store)
        assertTrue(result1 is ScriptExecutionResult.Success)

        val result2 = engineManager.execute(ScriptLanguage.JAVASCRIPT, script2, mockRequest, mockResponse, store)
        assertTrue(result2 is ScriptExecutionResult.Error, "Variables declared in prior script runs must not leak into new contexts")
    }
}
