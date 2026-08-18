package com.devuloopers.knet.core.http.auth

import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AuthCredentialsTest {

    @Test
    fun testAuthCredentialsCreation() {
        val bearerAuth = ApiRequestAuth.Bearer("token_12345")
        val basicAuth = ApiRequestAuth.Basic("user", "pass")
        val apiKeyAuth = ApiRequestAuth.ApiKey(value = "secret_key_99")

        assertEquals("token_12345", bearerAuth.token)

        assertEquals("user", basicAuth.username)
        assertEquals("pass", basicAuth.password)

        assertEquals("secret_key_99", apiKeyAuth.value)
    }

    @Test
    fun testNoneAuthType() {
        val noneAuth: ApiRequestAuth = ApiRequestAuth.None
        assertNotNull(noneAuth)
    }
}
