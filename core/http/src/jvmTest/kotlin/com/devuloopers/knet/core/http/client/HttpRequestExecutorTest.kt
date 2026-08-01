package com.devuloopers.knet.core.http.client

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestExecutorTest {

    private val client = KNetApiClient()

    @Test
    fun testExecuteGetRequest() = runBlocking {
        val result = client.execute("https://httpbin.org/get", method = "GET")
        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun testExecutePutRequest() = runBlocking {
        val result = client.execute("https://httpbin.org/put", method = "PUT", body = "test_put_body")
        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun testExecuteDeleteRequest() = runBlocking {
        val result = client.execute("https://httpbin.org/delete", method = "DELETE")
        assertNotNull(result)
        if (result.statusCode == 200) {
            assertTrue(result.isSuccess)
        }
    }
}
