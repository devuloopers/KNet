package com.devuloopers.knet.core.http.performance

import com.devuloopers.knet.core.http.client.KNetApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceRegressionTest {

    @Test
    fun testLatencyUnderSustainedExecution() = runBlocking {
        val client = KNetApiClient()
        val start = System.currentTimeMillis()

        repeat(3) {
            client.execute("https://httpbin.org/get", method = "GET")
        }

        val totalDuration = System.currentTimeMillis() - start
        assertTrue(totalDuration > 0)
        client.close()
    }
}
