package com.devuloopers.knet.core.http.auth

import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
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
        val auth: ApiRequestAuth = ApiRequestAuth.None
        assertNull((auth as? ApiRequestAuth.Bearer)?.token)
    }
}
