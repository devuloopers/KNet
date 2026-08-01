package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.routing.DefaultProxyRoutingStrategy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyRoutingTest {

    @Test
    fun testDefaultProxyRoutingStrategyRules() {
        val strategy = DefaultProxyRoutingStrategy()

        assertTrue(strategy.shouldAttemptProxy(8888))
        assertFalse(strategy.shouldAttemptProxy(null))

        val connException = java.net.ConnectException("Connection refused")
        assertTrue(strategy.isProxyConnectionFailure(connException, 8888))

        val otherException = IllegalArgumentException("Bad input")
        assertFalse(strategy.isProxyConnectionFailure(otherException, 8888))
    }

    @Test
    fun testMockEngineDirectRoutingFallback() = runTest {
        val mockEngine = MockEngine { request ->
            respond(content = "Direct Response", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(proxyPort = null, customEngine = mockEngine)
        val result = client.execute("https://api.knet.dev/data")

        assertEquals(200, result.statusCode)
        assertEquals("Direct Response", result.responseBody)
    }
}
