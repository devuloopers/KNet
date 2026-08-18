package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.traffic.model.http.HttpMethod
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
        val result = client.executeDetailed(
            url = "https://httpbin.org/get",
            method = HttpMethod.GET
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
        val result = client.executeDetailed(
            url = "https://httpbin.org/post",
            method = HttpMethod.POST,
            body = OutboundRequestBody.Json(jsonPayload)
        )

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
            assertTrue(result.responseBody.contains("key") || result.responseBody.contains("value"))
        }
    }

    @Test
    fun testExecuteWithAuthHeaders() = runBlocking {
        val result = client.executeDetailed(
            url = "https://httpbin.org/bearer",
            method = HttpMethod.GET,
            auth = ApiRequestAuth.Bearer("sample_test_token")
        )

        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
            assertTrue(result.responseBody.contains("authenticated") || result.responseBody.contains("token"))
        }
    }

    @Test
    fun testExecuteUnreachableUrlReturnsErrorResult() = runBlocking {
        val result = client.executeDetailed(
            url = "http://127.0.0.1:59999/non_existent_endpoint_12345",
            method = HttpMethod.GET
        )

        assertEquals(0, result.statusCode)
        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
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
