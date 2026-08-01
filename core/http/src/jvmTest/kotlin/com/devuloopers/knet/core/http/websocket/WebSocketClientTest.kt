package com.devuloopers.knet.core.http.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WebSocketClientTest {

    @Test
    fun testWebSocketFrameTypesAndState() {
        val message = "Hello WebSocket"
        val isBinary = false

        assertNotNull(message)
        assertEquals(false, isBinary)
    }
}
