package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestExecutionIntegrationTest {

    @Test
    fun testFullRequestResponseIntegrationCycle() = runBlocking {
        val client = KNetApiClient()
        val result = client.executeDetailed("https://httpbin.org/get", method = HttpMethod.GET)

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
            assertTrue(result.latencyMs >= 0L)
        }
        client.close()
    }
}
