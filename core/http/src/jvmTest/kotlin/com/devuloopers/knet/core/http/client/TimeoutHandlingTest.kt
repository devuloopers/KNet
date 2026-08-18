package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimeoutHandlingTest {

    @Test
    fun testTimeoutHandlingOnUnresponsiveEndpoint() = runBlocking {
        val client = KNetApiClient()
        // Delay endpoint simulation or unreachable port
        val result = client.executeDetailed("http://10.255.255.1:81/timeout_test", method = HttpMethod.GET)

        assertNotNull(result)
        assertEquals(0, result.statusCode)
        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        client.close()
    }
}
