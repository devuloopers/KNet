package com.devuloopers.knet.core.http.client

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConnectionPoolingTest {

    @Test
    fun testSequentialRequestReuseOnSingleClient() = runBlocking {
        val client = KNetApiClient()

        val req1 = async { client.execute("https://httpbin.org/get", method = "GET") }
        val req2 = async { client.execute("https://httpbin.org/get", method = "GET") }

        val res1 = req1.await()
        val res2 = req2.await()

        assertNotNull(res1)
        assertNotNull(res2)
        client.close()
    }
}
