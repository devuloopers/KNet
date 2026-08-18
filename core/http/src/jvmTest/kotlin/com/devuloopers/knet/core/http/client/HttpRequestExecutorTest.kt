package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestExecutorTest {

    private val client = KNetApiClient()

    @Test
    fun testExecuteGetRequest() = runBlocking {
        val result = client.executeDetailed("https://httpbin.org/get", method = HttpMethod.GET)
        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun testExecutePutRequest() = runBlocking {
        val result = client.executeDetailed(
            "https://httpbin.org/put",
            method = HttpMethod.PUT,
            body = OutboundRequestBody.Text("test_put_body"),
        )
        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun testExecuteDeleteRequest() = runBlocking {
        val result = client.executeDetailed("https://httpbin.org/delete", method = HttpMethod.DELETE)
        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
    }
}
