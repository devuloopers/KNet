package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.http.model.AuthType
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
        val result = client.execute(
            url = "https://api.knet.dev/protected",
            authType = AuthType.BEARER_TOKEN,
            authToken = "secret-token-123"
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
        val result = client.execute(
            url = "https://api.knet.dev/admin",
            authType = AuthType.BASIC_AUTH,
            authToken = "admin:password"
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
        val result = client.execute(
            url = "https://api.knet.dev/data",
            authType = AuthType.API_KEY,
            authToken = "key-xyz-789"
        )

        assertTrue(result.isSuccess)
    }
}
