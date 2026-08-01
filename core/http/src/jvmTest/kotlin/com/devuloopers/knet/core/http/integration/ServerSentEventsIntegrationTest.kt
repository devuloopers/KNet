package com.devuloopers.knet.core.http.integration

import com.devuloopers.knet.core.http.client.KNetApiClient
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServerSentEventsIntegrationTest {

    @Test
    fun testSSEIntegrationSetup() {
        val client = KNetApiClient()
        assertNotNull(client)
        client.close()
    }
}
