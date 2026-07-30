package com.devuloopers.knet.scriptengine.integration

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
 * Integration test suite for multi-language mixed execution pipelines.
 * Verifies sequential execution chains (JS -> Kotlin -> JS -> Kotlin) sharing the same [EnvironmentStore].
 */
class MixedEngineTest {

    private val engineManager = ScriptEngineManager()
    private val request = ScriptRequestModel("http://localhost:9090/api/test", "GET", mutableMapOf(), mutableMapOf(), "")
    private val response = ScriptResponseModel(200, "OK", 50L, 100L, emptyMap(), """{"status":200}""")

    /**
     * Verifies that JavaScript sets environment variables, Kotlin reads/modifies them, and JS reads the updated state.
     */
    @Test
    fun testMixedJsAndKotlinExecutionChain() = runBlocking {
        val store = EnvironmentStore()

        // Step 1: JS sets initial environment variable
        val jsScript1 = """
            pm.environment.set("auth_token", "js_token_100");
        """.trimIndent()

        val res1 = engineManager.execute(ScriptLanguage.JAVASCRIPT, jsScript1, request, response, store)
        assertTrue(res1 is ScriptExecutionResult.Success)
        assertEquals("js_token_100", store.get("auth_token"))

        // Step 2: Kotlin modifies environment variable
        val kotlinScript = """
            env["auth_token"] = "kotlin_token_200"
            env["kotlin_added"] = "true"
        """.trimIndent()

        val res2 = engineManager.execute(ScriptLanguage.KOTLIN, kotlinScript, request, response, store)
        assertTrue(res2 is ScriptExecutionResult.Success)
        assertEquals("kotlin_token_200", store.get("auth_token"))
        assertEquals("true", store.get("kotlin_added"))

        // Step 3: JS reads the environment variable set by Kotlin
        val jsScript2 = """
            pm.test("Token set by Kotlin", function() {
                var token = pm.environment.get("auth_token");
                pm.expect(token).to.eql("kotlin_token_200");
            });
        """.trimIndent()

        val res3 = engineManager.execute(ScriptLanguage.JAVASCRIPT, jsScript2, request, response, store)
        assertTrue(res3 is ScriptExecutionResult.Success)
        val success3 = res3 as ScriptExecutionResult.Success
        assertTrue(success3.testResults.all { it.passed })
    }
}
