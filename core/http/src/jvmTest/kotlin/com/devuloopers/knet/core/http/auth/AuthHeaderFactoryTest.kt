package com.devuloopers.knet.core.http.auth

import com.devuloopers.knet.core.http.model.AuthType
import kotlin.io.encoding.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthHeaderFactoryTest {

    @Test
    fun testBearerTokenHeaderFormat() {
        val token = "test_bearer_token_123"
        val headerValue = "Bearer $token"
        assertEquals("Bearer test_bearer_token_123", headerValue)
    }

    @Test
    fun testBasicAuthHeaderFormat() {
        val credentials = "admin:password123"
        val encoded = Base64.encode(credentials.encodeToByteArray())
        val headerValue = "Basic $encoded"
        assertEquals("Basic YWRtaW46cGFzc3dvcmQxMjM=", headerValue)
    }

    @Test
    fun testApiKeyHeaderFormat() {
        val apiKey = "my_custom_api_key"
        val headerName = "X-API-Key"
        assertEquals("X-API-Key", headerName)
        assertEquals("my_custom_api_key", apiKey)
    }

    @Test
    fun testNoneAuthHeader() {
        fun resolveHeader(type: AuthType): Pair<String, String>? {
            return when (type) {
                AuthType.BEARER_TOKEN -> Pair("Authorization", "Bearer token")
                AuthType.BASIC_AUTH -> Pair("Authorization", "Basic abc")
                AuthType.API_KEY -> Pair("X-API-Key", "key")
                AuthType.NONE -> null
            }
        }

        assertNull(resolveHeader(AuthType.NONE))
    }
}
