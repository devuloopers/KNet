package com.devuloopers.knet.core.http.auth

import com.devuloopers.knet.core.http.model.AuthType
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthTypeTest {

    @Test
    fun testAllAuthTypeEnumConstants() {
        assertEquals(4, AuthType.entries.size)
        assertEquals(AuthType.NONE, AuthType.valueOf("NONE"))
        assertEquals(AuthType.BEARER_TOKEN, AuthType.valueOf("BEARER_TOKEN"))
        assertEquals(AuthType.BASIC_AUTH, AuthType.valueOf("BASIC_AUTH"))
        assertEquals(AuthType.API_KEY, AuthType.valueOf("API_KEY"))
    }
}
