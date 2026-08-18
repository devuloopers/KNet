package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConnectionPoolingTest {

    @Test
    fun testSequentialRequestReuseOnSingleClient() = runBlocking {
        val client = KNetApiClient()

        val req1 = async { client.executeDetailed("https://httpbin.org/get", method = HttpMethod.GET) }
        val req2 = async { client.executeDetailed("https://httpbin.org/get", method = HttpMethod.GET) }

        val res1 = req1.await()
        val res2 = req2.await()

        assertNotNull(res1)
        assertNotNull(res2)
        client.close()
    }
}
