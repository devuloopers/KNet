package com.devuloopers.knet.engine.proxy.integration

import io.netty.handler.codec.http.HttpVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolIntegrationTest {

    @Test
    fun testProtocolVersionParsing() {
        val version11 = HttpVersion.HTTP_1_1
        val version10 = HttpVersion.HTTP_1_0

        assertEquals("HTTP/1.1", version11.text())
        assertEquals("HTTP/1.0", version10.text())
    }
}
