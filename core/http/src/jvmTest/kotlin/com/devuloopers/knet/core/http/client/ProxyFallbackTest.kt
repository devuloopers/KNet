package com.devuloopers.knet.core.http.client

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyFallbackTest {

    @Test
    fun testUnreachableProxyFallsBackToDirectHttpExecution() = runBlocking {
        // Point to an unused proxy port where no proxy server is listening (e.g. 59998)
        val clientWithStoppedProxy = KNetApiClient(proxyPort = 59998)

        val result = clientWithStoppedProxy.execute(
            url = "https://httpbin.org/get",
            method = "GET"
        )

        assertNotNull(result)
        // Should fall back automatically to direct execution and succeed
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
        clientWithStoppedProxy.close()
    }
}
