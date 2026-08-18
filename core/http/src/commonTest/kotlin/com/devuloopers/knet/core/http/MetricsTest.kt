package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.http.HttpMethod
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
    fun testExchangeTimingsPopulationInResult() = runTest {
        val mockEngine = MockEngine { request ->
            respond(content = "Response Payload Data", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(url = "https://api.knet.dev/metrics", method = HttpMethod.GET)

        assertEquals(200, result.statusCode)
        assertEquals(21L, result.responseSizeBytes)
        assertNotNull(result.timings.totalMillis)
        assertTrue(requireNotNull(result.timings.totalMillis) >= 0L)
    }

    @Test
    fun testExchangeTimingsDefaults() {
        val defaultMetrics = ExchangeTimings()
        assertEquals(null, defaultMetrics.totalMillis)
        assertEquals(null, defaultMetrics.dnsMillis)
        assertEquals(null, defaultMetrics.connectMillis)
        assertEquals(null, defaultMetrics.tlsMillis)
    }
}
