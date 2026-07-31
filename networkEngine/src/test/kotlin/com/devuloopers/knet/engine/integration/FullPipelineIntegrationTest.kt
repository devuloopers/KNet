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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Primary End-to-End Integration Test Suite validating KNet's entire request execution pipeline.
 *
 * Pipeline Flow:
 * ScriptRequestModel -> Pre-request Script -> Header Mutation -> KNetApiClient (Ktor CIO) ->
 * Spring Boot Test Server (:testingServer) -> ApiExecutionResult -> ScriptResponseModel ->
 * Test Script Assertions -> ExecutionResult.
 */
class FullPipelineIntegrationTest {

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
     * Executes the complete end-to-end pipeline with Kotlin pre-request and test scripts.
     */
    @Test
    fun testFullPipelineWithKotlinScripts(): Unit = runBlocking {
        val environmentStore = EnvironmentStore()

        // 1. Initial Request Definition
        val requestModel = ScriptRequestModel(
            url = "http://127.0.0.1:9090/api/test/headers",
            method = "GET",
            headers = mutableMapOf(),
            queryParams = mutableMapOf(),
            body = ""
        )

        // 2. Pre-request Script Execution (injects dynamic auth header and environment variable)
        val preScriptCode = """
            val reqId = "req_e2e_" + System.currentTimeMillis()
            request.headers["X-Pipeline-ID"] = reqId
            request.headers["X-Custom-Auth"] = "SecretKNetToken"
            env["current_req_id"] = reqId
        """.trimIndent()

        val preResult = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = preScriptCode,
            request = requestModel,
            response = ScriptResponseModel(200, "OK", 0L, 0L, emptyMap(), ""),
            environment = environmentStore
        )

        assertTrue(preResult is ScriptExecutionResult.Success, "Pre-request script execution must succeed")
        val expectedReqId = environmentStore.get("current_req_id")
        assertNotNull(expectedReqId)
        assertEquals("SecretKNetToken", requestModel.headers["X-Custom-Auth"])

        // 3. Network Execution via KNetApiClient over real socket to :testingServer (port 9090)
        val apiResult = apiClient.execute(
            url = requestModel.url,
            method = requestModel.method,
            headers = requestModel.headers
        )

        assertEquals(200, apiResult.statusCode)
        assertNotNull(apiResult.responseBody)

        // 4. Map Network Response into ScriptResponseModel
        val responseModel = ScriptResponseModel(
            statusCode = apiResult.statusCode,
            statusText = apiResult.statusText,
            latencyMs = apiResult.latencyMs,
            responseSizeBytes = apiResult.responseBody.length.toLong(),
            headers = apiResult.headers,
            body = apiResult.responseBody
        )

        // 5. Post-response Test Script Execution
        val testScriptCode = """
            test("Status Code is 200") {
                response.statusCode == 200
            }
            test("Pre-request X-Pipeline-ID was echoed by server") {
                response.body.contains("X-Pipeline-ID")
            }
            test("Environment variable persists across pipeline") {
                env["current_req_id"] == "$expectedReqId"
            }
        """.trimIndent()

        val postResult = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = testScriptCode,
            request = requestModel,
            response = responseModel,
            environment = environmentStore
        )

        assertTrue(postResult is ScriptExecutionResult.Success, "Post-response test script execution must succeed")
        assertEquals(3, postResult.testResults.size)
        assertTrue(postResult.testResults.all { it.passed }, "All end-to-end assertions must pass")
    }

    /**
     * Executes the complete end-to-end pipeline with JavaScript pre-request and test scripts.
     */
    @Test
    fun testFullPipelineWithJavaScriptScripts(): Unit = runBlocking {
        val environmentStore = EnvironmentStore()

        val requestModel = ScriptRequestModel(
            url = "http://127.0.0.1:9090/api/test/headers",
            method = "GET",
            headers = mutableMapOf(),
            queryParams = mutableMapOf(),
            body = ""
        )

        val preScriptJs = """
            pm.environment.set("js_env_key", "js_env_val");
            pm.environment.set("js_token", "js_secret_token_100");
        """.trimIndent()

        val preResult = scriptEngineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = preScriptJs,
            request = requestModel,
            response = ScriptResponseModel(200, "OK", 0L, 0L, emptyMap(), ""),
            environment = environmentStore
        )

        assertTrue(preResult is ScriptExecutionResult.Success)
        assertEquals("js_secret_token_100", environmentStore.get("js_token"))

        val apiResult = apiClient.execute(
            url = requestModel.url,
            method = requestModel.method,
            headers = requestModel.headers
        )

        assertEquals(200, apiResult.statusCode)

        val responseModel = ScriptResponseModel(
            statusCode = apiResult.statusCode,
            statusText = apiResult.statusText,
            latencyMs = apiResult.latencyMs,
            responseSizeBytes = apiResult.responseBody.length.toLong(),
            headers = apiResult.headers,
            body = apiResult.responseBody
        )

        val testScriptJs = """
            pm.test("Status code is 200", function() {
                pm.response.to.have.status(200);
            });
            pm.test("Environment state verified", function() {
                var val = pm.environment.get("js_env_key");
                pm.expect(val).to.eql("js_env_val");
            });
        """.trimIndent()

        val postResult = scriptEngineManager.execute(
            language = ScriptLanguage.JAVASCRIPT,
            code = testScriptJs,
            request = requestModel,
            response = responseModel,
            environment = environmentStore
        )

        assertTrue(postResult is ScriptExecutionResult.Success)
        assertTrue(postResult.testResults.all { it.passed })
    }
}
