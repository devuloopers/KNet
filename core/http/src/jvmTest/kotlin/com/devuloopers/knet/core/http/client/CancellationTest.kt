package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationTest {

    @Test
    fun testCoroutineCancellationSafety() = runBlocking {
        val client = KNetApiClient()

        val job = launch {
            try {
                client.executeDetailed("https://httpbin.org/delay/10", method = HttpMethod.GET)
            } catch (_: CancellationException) {
                // Expected when coroutine is cancelled
            }
        }

        job.cancel()
        job.join()
        client.close()
        
        // Verify coroutine cancelled safely without hanging
        assertTrue(job.isCancelled)
    }
}
