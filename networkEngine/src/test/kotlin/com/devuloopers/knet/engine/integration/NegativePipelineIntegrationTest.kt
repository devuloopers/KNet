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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration test suite for negative pipeline scenarios.
 *
 * Verifies that when a required pre-request header mutation is omitted, the network request proceeds
 * but the post-response test assertion correctly fails.
 */
class NegativePipelineIntegrationTest {

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
     * Verifies that omitting a required pre-request header causes the post-response test assertion to fail cleanly.
     */
    @Test
    fun testPipelineFailsWhenRequiredHeaderOmitted(): Unit = runBlocking {
        val environmentStore = EnvironmentStore()

        // 1. Initial Request with NO headers
        val requestModel = ScriptRequestModel(
            url = "http://127.0.0.1:9090/api/test/headers",
            method = "GET",
            headers = mutableMapOf(),
            queryParams = mutableMapOf(),
            body = ""
        )

        // 2. NO Pre-request script execution (header is NOT injected)

        // 3. Network Call
        val apiResult = apiClient.execute(
            url = requestModel.url,
            method = requestModel.method,
            headers = requestModel.headers
        )

        assertEquals(200, apiResult.statusCode)

        // 4. Map Network Response
        val responseModel = ScriptResponseModel(
            statusCode = apiResult.statusCode,
            statusText = apiResult.statusText,
            latencyMs = apiResult.latencyMs,
            responseSizeBytes = apiResult.responseBody.length.toLong(),
            headers = apiResult.headers,
            body = apiResult.responseBody
        )

        // 5. Test Script requiring "X-Required-Header"
        val testScriptCode = """
            test("Required header was echoed in response") {
                response.body.contains("X-Required-Header")
            }
        """.trimIndent()

        val postResult = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = testScriptCode,
            request = requestModel,
            response = responseModel,
            environment = environmentStore
        )

        assertTrue(postResult is ScriptExecutionResult.Success)
        assertEquals(1, postResult.testResults.size)
        assertFalse(postResult.testResults[0].passed, "Assertion must fail when required header was omitted")
    }
}
