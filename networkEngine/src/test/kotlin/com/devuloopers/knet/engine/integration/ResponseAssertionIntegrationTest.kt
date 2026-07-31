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
 * Integration test suite verifying post-response test script assertions against live response models.
 */
class ResponseAssertionIntegrationTest {

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
     * Verifies passing test script assertions evaluating status code and response body.
     */
    @Test
    fun testPassingResponseScriptAssertions(): Unit = runBlocking {
        val apiResult = apiClient.execute("http://127.0.0.1:9090/api/test/get", "GET")

        val scriptResponse = ScriptResponseModel(
            statusCode = apiResult.statusCode,
            statusText = apiResult.statusText,
            latencyMs = apiResult.latencyMs,
            responseSizeBytes = apiResult.responseBody.length.toLong(),
            headers = apiResult.headers,
            body = apiResult.responseBody
        )

        val testScript = """
            test("Status Code is 200") {
                response.statusCode == 200
            }
            test("Response time is fast") {
                response.latencyMs < 10000
            }
        """.trimIndent()

        val mockRequest = ScriptRequestModel("http://127.0.0.1:9090/api/test/get", "GET", mutableMapOf(), mutableMapOf(), "")
        val result = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = testScript,
            request = mockRequest,
            response = scriptResponse,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals(2, result.testResults.size)
        assertTrue(result.testResults.all { it.passed })
    }

    /**
     * Verifies failing test script assertions when status code mismatch occurs.
     */
    @Test
    fun testFailingResponseScriptAssertions(): Unit = runBlocking {
        val apiResult = apiClient.execute("http://127.0.0.1:9090/api/test/get", "GET")

        val scriptResponse = ScriptResponseModel(
            statusCode = apiResult.statusCode,
            statusText = apiResult.statusText,
            latencyMs = apiResult.latencyMs,
            responseSizeBytes = apiResult.responseBody.length.toLong(),
            headers = apiResult.headers,
            body = apiResult.responseBody
        )

        // Script expecting HTTP 404 on a 200 endpoint
        val testScript = """
            test("Expect Status 404") {
                response.statusCode == 404
            }
        """.trimIndent()

        val mockRequest = ScriptRequestModel("http://127.0.0.1:9090/api/test/get", "GET", mutableMapOf(), mutableMapOf(), "")
        val result = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = testScript,
            request = mockRequest,
            response = scriptResponse,
            environment = EnvironmentStore()
        )

        assertTrue(result is ScriptExecutionResult.Success)
        assertEquals(1, result.testResults.size)
        assertFalse(result.testResults[0].passed)
    }
}
