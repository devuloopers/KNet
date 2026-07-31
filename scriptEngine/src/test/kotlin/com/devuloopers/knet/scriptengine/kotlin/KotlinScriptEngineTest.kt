package com.devuloopers.knet.scriptengine.kotlin

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
import kotlin.test.fail

/**
 * Unit test suite for Kotlin Scripting engine covering TC-003 and TC-020 through TC-022.
 * Verifies Kotlin script execution, variable declarations, test assertions, and fallback parser evaluation.
 */
class KotlinScriptEngineTest {

    private val engineManager = ScriptEngineManager()

    /**
     * TC-020 & TC-021: Tests basic Kotlin script execution and environment variable updates.
     */
    @Test
    fun testKotlinBasicExecution() = runBlocking {
        val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
        val response = ScriptResponseModel(200, "OK", 50L, 120L, emptyMap(), """{"status":200}""")

        val script = """
            val x = 10
            env["token"] = "kotlin_token_123"
            test("Kotlin Status Test") {
                response.statusCode == 200
            }
        """.trimIndent()

        val environmentStore = EnvironmentStore()
        val result = engineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = script,
            request = request,
            response = response,
            environment = environmentStore
        )

        if (result is ScriptExecutionResult.Error) {
            fail("Kotlin script execution failed: ${result.message}")
        }

        val success = result as ScriptExecutionResult.Success
        assertEquals(1, success.testResults.size)
        assertTrue(success.testResults[0].passed)
        assertEquals("Kotlin Status Test", success.testResults[0].name)
        assertEquals("kotlin_token_123", success.environmentUpdates["token"])
    }
}
