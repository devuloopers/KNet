package com.devuloopers.knet.engine.integration

import com.devuloopers.knet.engine.client.KNetApiClient
import com.devuloopers.knet.scriptengine.api.EnvironmentStore
import com.devuloopers.knet.scriptengine.api.ScriptExecutionResult
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.scriptengine.api.ScriptRequestModel
import com.devuloopers.knet.scriptengine.api.ScriptResponseModel
import com.devuloopers.knet.scriptengine.core.ScriptEngineManager
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test suite validating environment variable state propagation across the execution pipeline.
 */
class EnvironmentVariableIntegrationTest {

    companion object {
        private lateinit var apiClient: KNetApiClient
        private val scriptEngineManager = ScriptEngineManager()

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            TestServerLifecycleManager.ensureServerRunning(9090)
            apiClient = KNetApiClient()
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            apiClient.close()
        }
    }

    /**
     * Verifies environment state modified in JS Pre-script is read in Kotlin Post-script.
     */
    @Test
    fun testEnvironmentStatePropagationAcrossPipeline(): Unit = runBlocking {
        val sharedStore = EnvironmentStore()
        val request = ScriptRequestModel("http://127.0.0.1:9090/api/test/get", "GET", mutableMapOf(), mutableMapOf(), "")

        // Step 1: Pre-request JS sets environment variable
        val preScriptJs = """
            pm.environment.set("auth_session_id", "session_abc_999");
        """.trimIndent()

        val preResult = scriptEngineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = preScriptJs,
            request = request,
            response = ScriptResponseModel(200, "OK", 0L, 0L, emptyMap(), ""),
            environment = sharedStore
        )

        assertTrue(preResult is ScriptExecutionResult.Success)
        assertEquals("session_abc_999", sharedStore.get("auth_session_id"))

        // Step 2: Real network call
        val apiResult = apiClient.execute(request.url, request.method)
        assertEquals(200, apiResult.statusCode)

        // Step 3: Post-response Kotlin test script verifies environment variable set in Step 1
        val scriptResponse = ScriptResponseModel(
            statusCode = apiResult.statusCode,
            statusText = apiResult.statusText,
            latencyMs = apiResult.latencyMs,
            responseSizeBytes = apiResult.responseBody.length.toLong(),
            headers = apiResult.headers,
            body = apiResult.responseBody
        )

        val postScriptKotlin = """
            test("Session ID matches pre-request setting") {
                env["auth_session_id"] == "session_abc_999"
            }
        """.trimIndent()

        val postResult = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = postScriptKotlin,
            request = request,
            response = scriptResponse,
            environment = sharedStore
        )

        assertTrue(postResult is ScriptExecutionResult.Success)
        assertTrue(postResult.testResults[0].passed)
    }
}
