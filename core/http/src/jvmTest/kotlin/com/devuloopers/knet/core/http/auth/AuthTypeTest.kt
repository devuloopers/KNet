package com.devuloopers.knet.core.http.auth

import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthTypeTest {

    @Test
    fun testAuthVariantsExposeStableTypeLabels() {
        assertEquals("No Auth", ApiRequestAuth.None.type)
        assertEquals("Bearer Token", ApiRequestAuth.Bearer("token").type)
        assertEquals("Basic Auth", ApiRequestAuth.Basic("user", "pass").type)
        assertEquals("API Key", ApiRequestAuth.ApiKey(value = "key").type)
    }
}
