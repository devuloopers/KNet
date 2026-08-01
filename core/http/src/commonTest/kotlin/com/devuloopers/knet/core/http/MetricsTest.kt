package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.model.HttpMetrics
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetricsTest {

    @Test
    fun testHttpMetricsPopulationInResult() = runTest {
        val mockEngine = MockEngine { request ->
            respond(content = "Response Payload Data", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.execute("https://api.knet.dev/metrics")

        assertEquals(200, result.statusCode)
        assertEquals(21L, result.responseSizeBytes)
        assertNotNull(result.metrics)
        assertTrue(result.metrics.totalTimeMs >= 0L)
    }

    @Test
    fun testHttpMetricsModelDefaults() {
        val defaultMetrics = HttpMetrics()
        assertEquals(0L, defaultMetrics.totalTimeMs)
        assertEquals(null, defaultMetrics.dnsTimeMs)
        assertEquals(null, defaultMetrics.tcpTimeMs)
        assertEquals(null, defaultMetrics.tlsTimeMs)
    }
}
