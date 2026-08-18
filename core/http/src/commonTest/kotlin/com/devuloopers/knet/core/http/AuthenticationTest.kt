package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticationTest {

    @Test
    fun testBearerTokenAuthentication() = runTest {
        val mockEngine = MockEngine { request ->
            val authHeader = request.headers["Authorization"]
            assertEquals("Bearer secret-token-123", authHeader)
            respond(content = "OK", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(
            url = "https://api.knet.dev/protected",
            auth = ApiRequestAuth.Bearer("secret-token-123")
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun testBasicAuthentication() = runTest {
        val mockEngine = MockEngine { request ->
            val authHeader = request.headers["Authorization"]
            assertTrue(authHeader?.startsWith("Basic ") == true)
            respond(content = "OK", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(
            url = "https://api.knet.dev/admin",
            auth = ApiRequestAuth.Basic("admin", "password")
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun testApiKeyAuthentication() = runTest {
        val mockEngine = MockEngine { request ->
            val apiKey = request.headers["X-API-Key"]
            assertEquals("key-xyz-789", apiKey)
            respond(content = "OK", status = HttpStatusCode.OK, headers = headersOf())
        }

        val client = KNetApiClient(customEngine = mockEngine)
        val result = client.executeDetailed(
            url = "https://api.knet.dev/data",
            auth = ApiRequestAuth.ApiKey(value = "key-xyz-789")
        )

        assertTrue(result.isSuccess)
    }
}
