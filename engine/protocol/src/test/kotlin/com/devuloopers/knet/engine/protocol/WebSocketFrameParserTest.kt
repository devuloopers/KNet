package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.websocket.WebSocketFrameParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebSocketFrameParserTest {

    @Test
    fun testParseTextFrame() {
        val parser = WebSocketFrameParser()
        val frame = TestFixtures.createTextWebSocketFrame("Hello World")
        val record = parser.parseFrame(frame, FrameDirection.CLIENT_TO_SERVER)

        assertNotNull(record)
        assertEquals(FrameType.TEXT, record.type)
        assertEquals("Hello World", record.payloadText)
    }

    @Test
    fun testParseBinaryFrame() {
        val parser = WebSocketFrameParser()
        val frame = TestFixtures.createBinaryWebSocketFrame(byteArrayOf(0x0A, 0x0B, 0x0C))
        val record = parser.parseFrame(frame, FrameDirection.SERVER_TO_CLIENT)

        assertNotNull(record)
        assertEquals(FrameType.BINARY, record.type)
        assertEquals("0A0B0C", record.payloadHex)
    }

    @Test
    fun testParseCloseFrame() {
        val parser = WebSocketFrameParser()
        val frame = TestFixtures.createCloseWebSocketFrame(1000, "Normal Closure")
        val record = parser.parseFrame(frame, FrameDirection.CLIENT_TO_SERVER)

        assertNotNull(record)
        assertEquals(FrameType.CLOSE, record.type)
        val text = record.payloadText
        assertNotNull(text)
        assertTrue(text.contains("Close code: 1000"))
        assertTrue(text.contains("Normal Closure"))
    }

    @Test
    fun testParsePingFrame() {
        val parser = WebSocketFrameParser()
        val frame = TestFixtures.createPingWebSocketFrame()
        val record = parser.parseFrame(frame, FrameDirection.CLIENT_TO_SERVER)

        assertNotNull(record)
        assertEquals(FrameType.PING, record.type)
    }

    @Test
    fun testParsePongFrame() {
        val parser = WebSocketFrameParser()
        val frame = TestFixtures.createPongWebSocketFrame()
        val record = parser.parseFrame(frame, FrameDirection.SERVER_TO_CLIENT)

        assertNotNull(record)
        assertEquals(FrameType.PONG, record.type)
    }

    @Test
    fun testPayloadTruncationSafety() {
        val parser = WebSocketFrameParser(maxInspectionBytes = 5)
        val frame = TestFixtures.createTextWebSocketFrame("1234567890")
        val record = parser.parseFrame(frame, FrameDirection.CLIENT_TO_SERVER)

        assertNotNull(record)
        val text = record.payloadText
        assertNotNull(text)
        assertTrue(text.contains("[Truncated]"))
    }
}
