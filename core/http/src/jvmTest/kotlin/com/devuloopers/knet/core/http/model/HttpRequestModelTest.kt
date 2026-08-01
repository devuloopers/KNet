package com.devuloopers.knet.core.http.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRequestModelTest {

    @Test
    fun testHttpRequestParameters() {
        val url = "https://api.example.com/v1/users"
        val method = "POST"
        val headers = mapOf("Accept" to "application/json")
        val body = "{\"name\":\"Alice\"}"

        assertEquals("https://api.example.com/v1/users", url)
        assertEquals("POST", method)
        assertEquals("application/json", headers["Accept"])
        assertEquals("{\"name\":\"Alice\"}", body)
    }
}
