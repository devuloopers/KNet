package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.model.AuthType
import com.devuloopers.knet.core.http.model.RequestBodyType
import com.devuloopers.knet.domain.clientNetwork.model.KNetHeaders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KNetApiClientTest {

    private val client = KNetApiClient()

    @Test
    fun testExecuteDirectHttpGetRequest() = runBlocking {
        val result = client.execute(
            url = "https://httpbin.org/get",
            method = "GET"
        )

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
            assertTrue(result.responseBody.contains("origin") || result.responseBody.contains("headers"))
        }
    }

    @Test
    fun testExecuteDirectHttpPostJsonRequest() = runBlocking {
        val jsonPayload = """{"key":"value"}"""
        val result = client.execute(
            url = "https://httpbin.org/post",
            method = "POST",
            body = jsonPayload,
            bodyType = RequestBodyType.JSON
        )

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
            assertTrue(result.responseBody.contains("key") || result.responseBody.contains("value"))
        }
    }

    @Test
    fun testExecuteWithAuthHeaders() = runBlocking {
        val result = client.execute(
            url = "https://httpbin.org/bearer",
            method = "GET",
            authType = AuthType.BEARER_TOKEN,
            authToken = "sample_test_token"
        )

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
            assertTrue(result.responseBody.contains("authenticated") || result.responseBody.contains("token"))
        }
    }

    @Test
    fun testExecuteUnreachableUrlReturnsErrorResult() = runBlocking {
        val result = client.execute(
            url = "http://127.0.0.1:59999/non_existent_endpoint_12345",
            method = "GET"
        )

        assertEquals(0, result.statusCode)
        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testExecuteProxyDnsFailureTriggersTrafficListener() = runBlocking {
        var interceptedTransaction: com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction? = null
        
        val proxyTrafficListener = object : com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener {
            override fun onTransactionCaptured(transaction: com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction) {
                interceptedTransaction = transaction
            }
        }

        val clientWithListener = KNetApiClient(proxyTrafficListener = proxyTrafficListener)
        
        // Execute request to an unreachable domain with proxy turned ON (proxyPort = 59997)
        val result = clientWithListener.execute(
            url = "http://unreachable-domain-that-does-not-exist.local",
            method = "GET",
            proxyPort = 59997 // Proxy ON but pointing to non-existent port
        )

        // The execution result itself should still return the error properly
        assertEquals(0, result.statusCode)
        assertFalse(result.isSuccess)

        // BUT our listener should have ALSO intercepted it as a 502 Bad Gateway!
        assertNotNull("Proxy traffic listener was not invoked!", interceptedTransaction)
        assertEquals(502, interceptedTransaction?.response?.statusCode)
        assertEquals("Bad Gateway", interceptedTransaction?.response?.statusText)
        assertTrue(interceptedTransaction?.response?.headers?.any { it.first == KNetHeaders.HEADER_PROXY_ERROR } == true)
        
        clientWithListener.close()
    }

    @Test
    fun testUpdateTimeoutSecondsUpdatesConfiguration() {
        val testClient = KNetApiClient()
        testClient.updateTimeoutSeconds(45)
        assertEquals(45_000L, testClient.getConfiguration().timeoutMillis)
        assertEquals(45_000L, testClient.getConfiguration().connectTimeoutMillis)

        testClient.updateTimeoutSeconds(120)
        assertEquals(120_000L, testClient.getConfiguration().timeoutMillis)
        assertEquals(120_000L, testClient.getConfiguration().connectTimeoutMillis)
    }

    @Test
    fun testUpdateTimeoutMillisUpdatesConfiguration() {
        val testClient = KNetApiClient()
        testClient.updateTimeoutMillis(2500L)
        assertEquals(2500L, testClient.getConfiguration().timeoutMillis)
        assertEquals(2500L, testClient.getConfiguration().connectTimeoutMillis)

        // Lower bound clamp check
        testClient.updateTimeoutMillis(10L)
        assertEquals(100L, testClient.getConfiguration().timeoutMillis)
    }
}
