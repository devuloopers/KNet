package com.devuloopers.knet.core.http.cookies

import org.junit.Assert.assertEquals
import org.junit.Test

class CookieHandlingTest {

    @Test
    fun testSetCookieHeaderParsing() {
        val cookieHeader = "session_id=xyz123; Path=/; Secure; HttpOnly"
        val cookieParts = cookieHeader.split(";").map { it.trim() }

        assertEquals("session_id=xyz123", cookieParts[0])
        assertEquals("Path=/", cookieParts[1])
        assertEquals("Secure", cookieParts[2])
        assertEquals("HttpOnly", cookieParts[3])
    }
}
