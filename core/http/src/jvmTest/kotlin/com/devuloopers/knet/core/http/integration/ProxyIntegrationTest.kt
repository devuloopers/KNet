package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProxyIntegrationTest {

    @Test
    fun testProxyExecutionWithFallback() = runBlocking {
        val client = KNetApiClient(proxyPort = 59997)
        val result = client.executeDetailed("https://httpbin.org/get", method = HttpMethod.GET)

        assertNotNull(result)
        client.close()
    }
}
