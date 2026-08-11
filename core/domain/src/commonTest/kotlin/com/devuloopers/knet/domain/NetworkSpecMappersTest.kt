package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toNetworkRequestSpec
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toNetworkResponseSpec
import com.devuloopers.knet.domain.network.mapper.NetworkSpecMappers.toSavedApiRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkSpecMappersTest {

    @Test
    fun `HttpRequest converts to NetworkRequestSpec preserving headers and query parameters`() {
        val headers = listOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Set-Cookie" to "session=abc12345; Path=/",
            "Set-Cookie" to "theme=dark; Path=/"
        )

        val request = HttpRequest(
            id = "req_123",
            method = "POST",
            url = "https://api.knet.dev/v1/users?page=1&limit=20",
            protocol = "HTTP/2",
            headers = headers,
            body = "{\"name\":\"KNet Test User\"}".encodeToByteArray(),
            timestamp = 1754900000000L
        )

        val spec = request.toNetworkRequestSpec()

        assertEquals(HttpMethod.POST, spec.method)
        assertEquals("https://api.knet.dev/v1/users?page=1&limit=20", spec.url)
        assertEquals(4, spec.headers.size)
        assertEquals(2, spec.queryParams.size)
        assertEquals("page" to "1", spec.queryParams[0])
        assertEquals("limit" to "20", spec.queryParams[1])
        assertEquals(2, spec.cookies.size)
        assertEquals("session" to "abc12345", spec.cookies[0])
        assertEquals("theme" to "dark", spec.cookies[1])
        assertEquals("{\"name\":\"KNet Test User\"}", spec.bodyPayload)
        assertEquals(RequestBodyType.JSON, spec.bodyType)
        assertEquals(1754900000000L, spec.timestamp)
    }

    @Test
    fun `HttpResponse converts to NetworkResponseSpec preserving status and metrics`() {
        val response = HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = listOf("Content-Type" to "application/json"),
            body = "{\"status\":\"success\"}".encodeToByteArray(),
            timestamp = 1754900000000L
        )

        val spec = response.toNetworkResponseSpec(
            durationMs = 145L,
            sizeBytes = 1024L
        )

        assertEquals(200, spec.statusCode)
        assertEquals("OK", spec.statusText)
        assertEquals(145L, spec.durationMs)
        assertEquals(1024L, spec.sizeBytes)
        assertEquals("{\"status\":\"success\"}", spec.responseBody)
        assertTrue(spec.hasResponse)
    }

    @Test
    fun `NetworkRequestSpec converts to SavedApiRequest cleanly`() {
        val request = HttpRequest(
            id = "req_999",
            method = "PUT",
            url = "https://api.knet.dev/v1/items",
            protocol = "HTTP/1.1",
            headers = listOf("Authorization" to "Bearer token_xyz"),
            body = "{\"item\":\"test\"}".encodeToByteArray(),
            timestamp = 1754900000000L
        )

        val spec = request.toNetworkRequestSpec()
        val saved = spec.toSavedApiRequest("saved_1", "Updated Item Request")

        assertEquals("saved_1", saved.id)
        assertEquals("Updated Item Request", saved.name)
        assertEquals(HttpMethod.PUT, saved.method)
        assertEquals("https://api.knet.dev/v1/items", saved.url)
        assertEquals(1, saved.headers.size)
        assertEquals("Authorization", saved.headers[0].key)
        assertEquals("Bearer token_xyz", saved.headers[0].value)
    }
}
