package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestExecutionIntegrationTest {

    @Test
    fun testFullRequestResponseIntegrationCycle() = runBlocking {
        val client = KNetApiClient()
        val result = client.execute("https://httpbin.org/get", method = "GET")

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
            assertTrue(result.latencyMs >= 0L)
        }
        client.close()
    }
}
