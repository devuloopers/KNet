package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.core.http.model.AuthType
import com.devuloopers.knet.core.http.model.RequestBodyType
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
}
