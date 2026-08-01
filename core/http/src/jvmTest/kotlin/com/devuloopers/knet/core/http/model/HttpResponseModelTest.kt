package com.devuloopers.knet.core.http.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpResponseModelTest {

    @Test
    fun testHttpResponseModelProperties() {
        val statusCode = 200
        val statusText = "OK"
        val responseBody = "{\"status\":\"success\"}"

        assertEquals(200, statusCode)
        assertEquals("OK", statusText)
        assertEquals("{\"status\":\"success\"}", responseBody)
    }
}
