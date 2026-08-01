package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProxyIntegrationTest {

    @Test
    fun testProxyExecutionWithFallback() = runBlocking {
        val client = KNetApiClient(proxyPort = 59997)
        val result = client.execute("https://httpbin.org/get", method = "GET")

        assertNotNull(result)
        client.close()
    }
}
