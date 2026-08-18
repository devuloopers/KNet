package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.clientNetwork.executor.HttpExecutor
import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.traffic.model.http.HttpMethod
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KNetApiClientTest {

    @Test
    fun testHttpGetExecution() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("GET", request.method.value)
            assertEquals("https://api.knet.dev/users", request.url.toString())
            respond(
                content = "{\"users\":[]}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(url = "https://api.knet.dev/users", method = HttpMethod.GET)

        assertEquals(200, result.statusCode)
        assertEquals("OK", result.statusText)
        assertTrue(result.isSuccess)
        assertEquals("{\"users\":[]}", result.responseBody)
    }

    @Test
    fun testHttpPostExecution() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("POST", request.method.value)
            respond(
                content = "{\"id\":\"created-1\"}",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(
            url = "https://api.knet.dev/items",
            method = HttpMethod.POST,
            body = OutboundRequestBody.Json("{\"name\":\"Item 1\"}"),
        )

        assertEquals(201, result.statusCode)
        assertTrue(result.isSuccess)
        assertEquals("{\"id\":\"created-1\"}", result.responseBody)
    }

    @Test
    fun testHttpPutAndDeleteExecution() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf()
            )
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val putResult = client.executeDetailed(url = "https://api.knet.dev/items/1", method = HttpMethod.PUT)
        val deleteResult = client.executeDetailed(url = "https://api.knet.dev/items/1", method = HttpMethod.DELETE)

        assertEquals(200, putResult.statusCode)
        assertEquals(200, deleteResult.statusCode)
    }

    @Test
    fun `custom HTTP method reaches Ktor without case normalization`() = runTest {
        var receivedMethod = ""
        val mockEngine = MockEngine { request ->
            receivedMethod = request.method.value
            respond(content = "ok", status = HttpStatusCode.OK, headers = headersOf())
        }
        val client = KNetApiClient(customEngine = mockEngine)
        val executor: HttpExecutor = client

        executor.execute(
            url = "https://api.knet.dev/resource",
            method = HttpMethod.fromToken("customMethod"),
        )

        assertEquals("customMethod", receivedMethod)
    }
}
