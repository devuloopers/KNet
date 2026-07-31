package com.devuloopers.knet.scriptengine.regression

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
 * Dedicated regression test suite ensuring fixed bugs never resurface.
 */
class RegressionTest {

    private val engineManager = ScriptEngineManager()
    private val mockRequest = ScriptRequestModel("http://localhost", "GET", mutableMapOf(), mutableMapOf(), "")
    private val mockResponse = ScriptResponseModel(200, "OK", 10L, 100L, emptyMap(), """{"status":200}""")

    /**
     * REGRESSION BUG #1: "GraalJS Engine not available on JVM runtime"
     * Verifies GraalJS polyglot engine resolves cleanly on JVM runtime without missing engine errors.
     */
    @Test
    fun testReg001_GraalJsEngineResolution() = runBlocking {
        val script = "console.log('GraalJS Available');"
        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, EnvironmentStore())
        assertTrue(result is ScriptExecutionResult.Success, "GraalJS engine must resolve successfully")
    }

    /**
     * REGRESSION BUG #2: "TypeError: execute on ...$$Lambda failed due to Message not supported"
     * Verifies that ScriptHostBridge methods decorated with @HostAccess.Export can be called cleanly from JS.
     */
    @Test
    fun testReg002_HostBridgeExportMethodCall() = runBlocking {
        val script = """
            pm.test("Host bridge test", function() {
                pm.response.to.have.status(200);
            });
            pm.environment.set("reg_key", "reg_val");
        """.trimIndent()

        val store = EnvironmentStore()
        val result = engineManager.execute(ScriptLanguage.JAVASCRIPT, script, mockRequest, mockResponse, store)
        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals("reg_val", store.get("reg_key"))
    }

    /**
     * REGRESSION BUG #3: "Kotlin Syntax Error: Unrecognized statement env['key'] = 'val'"
     * Verifies that fallback Kotlin evaluator handles env key assignments with or without spaces around '='.
     */
    @Test
    fun testReg003_KotlinFallbackEnvAssignmentSpaces() = runBlocking {
        val script = """
            env["token"] = "kotlin_token_123"
            test("Status Check") {
                response.statusCode == 200
            }
        """.trimIndent()

        val store = EnvironmentStore()
        val result = engineManager.execute(ScriptLanguage.KOTLIN, script, mockRequest, mockResponse, store)
        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals("kotlin_token_123", store.get("token"))
    }
}
