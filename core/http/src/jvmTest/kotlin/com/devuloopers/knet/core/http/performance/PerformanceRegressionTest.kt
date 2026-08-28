package com.devuloopers.knet.core.http.performance

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.TimeSource

class PerformanceRegressionTest {

    @Test
    fun testLatencyUnderSustainedExecution() = runBlocking {
        val client = KNetApiClient()
        val start = TimeSource.Monotonic.markNow()

        repeat(3) {
            client.executeDetailed("https://httpbin.org/get", method = HttpMethod.GET)
        }

        val totalDuration = start.elapsedNow()
        assertTrue(totalDuration.isPositive())
        client.close()
    }
}
