package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
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
        val result = client.execute(url = "https://api.knet.dev/users", method = "GET")

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
        val result = client.execute(
            url = "https://api.knet.dev/items",
            method = "POST",
            body = "{\"name\":\"Item 1\"}"
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
        val putResult = client.execute(url = "https://api.knet.dev/items/1", method = "PUT")
        val deleteResult = client.execute(url = "https://api.knet.dev/items/1", method = "DELETE")

        assertEquals(200, putResult.statusCode)
        assertEquals(200, deleteResult.statusCode)
    }
}
