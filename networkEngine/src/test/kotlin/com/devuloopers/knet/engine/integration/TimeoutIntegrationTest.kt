package com.devuloopers.knet.engine.integration

import com.devuloopers.knet.engine.client.KNetApiClient
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration test suite for network connection timeouts and unreachable server endpoints.
 */
class TimeoutIntegrationTest {

    companion object {
        private lateinit var apiClient: KNetApiClient

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            apiClient = KNetApiClient()
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            apiClient.close()
        }
    }

    /**
     * Verifies that connecting to an unreachable port returns HTTP 0 status error result gracefully.
     */
    @Test
    fun testUnreachablePortReturnsErrorGracefully(): Unit = runBlocking {
        // Port 59998 has no listening server
        val result = apiClient.execute(
            url = "http://127.0.0.1:59998/api/test/timeout",
            method = "GET"
        )

        assertNotNull(result)
        assertEquals(0, result.statusCode, "Unreachable socket must return HTTP 0 error status")
    }
}
