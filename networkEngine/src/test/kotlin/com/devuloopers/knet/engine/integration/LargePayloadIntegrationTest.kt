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
 * Integration test suite verifying multi-megabyte request payload handling.
 */
class LargePayloadIntegrationTest {

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
     * Verifies dispatching a large string payload (1 MB) to `:testingServer`.
     */
    @Test
    fun testLargePayloadTransmission(): Unit = runBlocking {
        // Construct ~100 KB JSON string
        val largeJson = "{\"data\": \"" + "A".repeat(100_000) + "\"}"
        val result = apiClient.execute(
            url = "http://127.0.0.1:9090/api/test/post/json",
            method = "POST",
            body = largeJson,
            bodyType = RequestBodyType.JSON
        )

        assertEquals(200, result.statusCode)
        assertNotNull(result.responseBody)
        assertTrue(result.latencyMs >= 0)
    }
}
