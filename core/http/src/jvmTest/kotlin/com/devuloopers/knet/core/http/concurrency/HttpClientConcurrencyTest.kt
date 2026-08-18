package com.devuloopers.knet.core.http.concurrency

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpClientConcurrencyTest {

    @Test
    fun testConcurrentRequestsOnSharedClient() = runBlocking {
        val client = KNetApiClient()

        val tasks = (1..10).map {
            async {
                client.executeDetailed("https://httpbin.org/get", method = HttpMethod.GET)
            }
        }

        val results = tasks.awaitAll()
        assertEquals(10, results.size)
        client.close()
    }
}
