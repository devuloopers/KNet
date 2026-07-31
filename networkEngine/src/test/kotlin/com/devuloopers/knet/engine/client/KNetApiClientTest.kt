package com.devuloopers.knet.engine.client

import com.devuloopers.knet.engine.client.model.AuthType
import com.devuloopers.knet.engine.client.model.RequestBodyType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KNetApiClientTest {

    private lateinit var client: KNetApiClient

    @Before
    fun setUp() {
        client = KNetApiClient()
    }

    @After
    fun tearDown() {
        client.close()
    }

    @Test
    fun testExecuteGetRequest() = runBlocking {
        val result = client.execute(
            url = "https://httpbin.org/get",
            method = "GET"
        )
        assertNotNull(result)
        assertTrue(result.latencyMs > 0)
    }

    @Test
    fun testExecutePostJsonRequest() = runBlocking {
        val jsonPayload = "{\"test\": \"knet_collections\"}"
        val result = client.execute(
            url = "https://httpbin.org/post",
            method = "POST",
            body = jsonPayload,
            bodyType = RequestBodyType.JSON
        )
        assertNotNull(result)
        assertTrue(result.latencyMs > 0)
    }

    @Test
    fun testExecuteBearerAuthRequest() = runBlocking {
        val result = client.execute(
            url = "https://httpbin.org/bearer",
            method = "GET",
            authType = AuthType.BEARER_TOKEN,
            authToken = "sample_test_token"
        )
        assertNotNull(result)
    }

    @Test
    fun testConvenienceHelperMethods() = runBlocking {
        val getResult = client.get("https://httpbin.org/get")
        assertNotNull(getResult)

        val postResult = client.post("https://httpbin.org/post", body = "{\"key\":\"val\"}")
        assertNotNull(postResult)

        val putResult = client.put("https://httpbin.org/put", body = "{\"key\":\"val\"}")
        assertNotNull(putResult)

        val deleteResult = client.delete("https://httpbin.org/delete")
        assertNotNull(deleteResult)

        val patchResult = client.patch("https://httpbin.org/patch", body = "{\"key\":\"val\"}")
        assertNotNull(patchResult)
    }

    @Test
    fun testProxyFallbackWhenProxyIsStopped() = runBlocking {
        // Port 59999 has no active proxy server listening
        val inactiveProxyClient = KNetApiClient(proxyPort = 59999)
        val result = inactiveProxyClient.get("https://httpbin.org/get")
        assertNotNull(result)
        assertTrue(result.latencyMs > 0)
        inactiveProxyClient.close()
    }
}
