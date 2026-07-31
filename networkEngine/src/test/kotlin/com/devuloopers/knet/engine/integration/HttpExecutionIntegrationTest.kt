package com.devuloopers.knet.engine.integration

import com.devuloopers.knet.engine.client.KNetApiClient
import com.devuloopers.knet.engine.client.model.RequestBodyType
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test suite verifying HTTP request execution across verbs and payload types.
 *
 * Dispatches real HTTP GET, POST, PUT, DELETE, and PATCH calls via [KNetApiClient] to `:testingServer`.
 */
class HttpExecutionIntegrationTest {

    companion object {
        private lateinit var apiClient: KNetApiClient

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
     * Verifies HTTP GET execution against live endpoint.
     */
    @Test
    fun testHttpGetExecution(): Unit = runBlocking {
        val result = apiClient.execute(
            url = "http://127.0.0.1:9090/api/test/get",
            method = "GET"
        )

        assertEquals(200, result.statusCode)
        assertNotNull(result.responseBody)
        assertTrue(result.latencyMs >= 0)
    }

    /**
     * Verifies HTTP POST execution with JSON body payload.
     */
    @Test
    fun testHttpPostJsonExecution(): Unit = runBlocking {
        val payload = """{"service": "KNet", "module": "networkEngine"}"""
        val result = apiClient.execute(
            url = "http://127.0.0.1:9090/api/test/post/json",
            method = "POST",
            body = payload,
            bodyType = RequestBodyType.JSON
        )

        assertEquals(200, result.statusCode, "Expected 200 but got ${result.statusCode}: ${result.responseBody}")
        assertNotNull(result.responseBody)
    }

    /**
     * Verifies HTTP PUT execution with XML body payload.
     */
    @Test
    fun testHttpPutXmlExecution(): Unit = runBlocking {
        val payload = "<request><item>knet_test_payload</item></request>"
        val result = apiClient.execute(
            url = "http://127.0.0.1:9090/api/test/put",
            method = "PUT",
            body = payload,
            bodyType = RequestBodyType.XML
        )

        assertEquals(200, result.statusCode)
        assertNotNull(result.responseBody)
    }

    /**
     * Verifies HTTP DELETE execution.
     */
    @Test
    fun testHttpDeleteExecution(): Unit = runBlocking {
        val result = apiClient.execute(
            url = "http://127.0.0.1:9090/api/test/delete",
            method = "DELETE"
        )

        assertEquals(200, result.statusCode)
    }

    /**
     * Verifies HTTP PATCH execution.
     */
    @Test
    fun testHttpPatchExecution(): Unit = runBlocking {
        val payload = """{"patch_key": "patch_val"}"""
        val result = apiClient.execute(
            url = "http://127.0.0.1:9090/api/test/patch",
            method = "PATCH",
            body = payload,
            bodyType = RequestBodyType.JSON
        )

        assertEquals(200, result.statusCode)
    }
}
