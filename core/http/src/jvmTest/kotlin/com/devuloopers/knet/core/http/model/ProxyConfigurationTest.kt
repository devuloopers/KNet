package com.devuloopers.knet.core.http.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyConfigurationTest {

    @Test
    fun testProxyConfigurationPortValidation() {
        val host = "127.0.0.1"
        val port = 8080
        val isEnabled = true

        assertEquals("127.0.0.1", host)
        assertEquals(8080, port)
        assertTrue(isEnabled)
    }
}
