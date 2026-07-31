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
 * Integration test suite for pre-request script HTTP header and parameter mutations.
 *
 * Verifies that pre-request scripts executed via [ScriptEngineManager] mutate request headers
 * prior to dispatching real HTTP calls via [KNetApiClient] to the local testing server.
 */
class HeaderMutationIntegrationTest {

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
     * Verifies that Kotlin pre-request scripts inject custom HTTP headers into outgoing requests.
     */
    @Test
    fun testPreRequestHeaderInjection(): Unit = runBlocking {
        val environmentStore = EnvironmentStore()
        val initialRequest = ScriptRequestModel(
            url = "http://127.0.0.1:9090/api/test/headers",
            method = "GET",
            headers = mutableMapOf("User-Agent" to "KNet-TestClient"),
            queryParams = mutableMapOf(),
            body = ""
        )

        // Pre-request Kotlin script injecting X-Custom-Header and X-Timestamp
        val preScript = """
            val timestamp = System.currentTimeMillis().toString()
            request.headers["X-Custom-Header"] = "KNetInjectedValue"
            request.headers["X-Timestamp"] = timestamp
            env["injected_timestamp"] = timestamp
        """.trimIndent()

        val preResult = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = preScript,
            request = initialRequest,
            response = ScriptResponseModel(200, "OK", 0L, 0L, emptyMap(), ""),
            environment = environmentStore
        )

        assertTrue(preResult is ScriptExecutionResult.Success, "Pre-request script execution must succeed")
        assertEquals("KNetInjectedValue", initialRequest.headers["X-Custom-Header"])

        // Dispatch real HTTP call with mutated headers
        val apiResult = apiClient.execute(
            url = initialRequest.url,
            method = initialRequest.method,
            headers = initialRequest.headers
        )

        assertEquals(200, apiResult.statusCode)
        assertNotNull(apiResult.responseBody)
        assertTrue(apiResult.responseBody.contains("X-Custom-Header"))
    }

    /**
     * Verifies that JavaScript pre-request scripts overwrite existing header values.
     */
    @Test
    fun testPreRequestHeaderOverwriteJs(): Unit = runBlocking {
        val environmentStore = EnvironmentStore()
        val initialRequest = ScriptRequestModel(
            url = "http://127.0.0.1:9090/api/test/headers",
            method = "GET",
            headers = mutableMapOf("X-Tenant-ID" to "tenant_old_100"),
            queryParams = mutableMapOf(),
            body = ""
        )

        val preScript = """
            request.headers["X-Tenant-ID"] = "tenant_new_999"
            env["tenant_updated"] = "true"
        """.trimIndent()

        val preResult = scriptEngineManager.execute(
            language = ScriptLanguage.KOTLIN,
            code = preScript,
            request = initialRequest,
            response = ScriptResponseModel(200, "OK", 0L, 0L, emptyMap(), ""),
            environment = environmentStore
        )

        assertTrue(preResult is ScriptExecutionResult.Success)
        assertEquals("tenant_new_999", initialRequest.headers["X-Tenant-ID"])
        assertEquals("true", environmentStore.get("tenant_updated"))

        val apiResult = apiClient.execute(
            url = initialRequest.url,
            method = initialRequest.method,
            headers = initialRequest.headers
        )

        assertEquals(200, apiResult.statusCode)
        assertTrue(apiResult.responseBody.contains("tenant_new_999"))
    }
}
