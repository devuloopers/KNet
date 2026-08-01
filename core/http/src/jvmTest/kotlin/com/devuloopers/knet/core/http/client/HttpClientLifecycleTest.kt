package com.devuloopers.knet.core.http.client

import org.junit.Assert.assertNotNull
import org.junit.Test

class HttpClientLifecycleTest {

    @Test
    fun testClientInstantiationAndClose() {
        val client = KNetApiClient()
        assertNotNull(client)
        client.close()
    }

    @Test
    fun testClientWithProxyPortInstantiationAndClose() {
        val client = KNetApiClient(proxyPort = 8080)
        assertNotNull(client)
        client.close()
    }
}
