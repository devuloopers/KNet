package com.devuloopers.knet.core.http.auth

import com.devuloopers.knet.core.http.model.AuthType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AuthCredentialsTest {

    @Test
    fun testAuthCredentialsCreation() {
        val bearerAuth = Pair(AuthType.BEARER_TOKEN, "token_12345")
        val basicAuth = Pair(AuthType.BASIC_AUTH, "user:pass")
        val apiKeyAuth = Pair(AuthType.API_KEY, "secret_key_99")

        assertEquals(AuthType.BEARER_TOKEN, bearerAuth.first)
        assertEquals("token_12345", bearerAuth.second)

        assertEquals(AuthType.BASIC_AUTH, basicAuth.first)
        assertEquals("user:pass", basicAuth.second)

        assertEquals(AuthType.API_KEY, apiKeyAuth.first)
        assertEquals("secret_key_99", apiKeyAuth.second)
    }

    @Test
    fun testNoneAuthType() {
        val noneAuth = Pair(AuthType.NONE, "")
        assertEquals(AuthType.NONE, noneAuth.first)
        assertNotNull(noneAuth.second)
    }
}
