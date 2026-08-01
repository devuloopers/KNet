package com.devuloopers.knet.engine.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebSocketFrameRecordTest {

    @Test
    fun testWebSocketFrameRecordProperties() {
        val record = WebSocketFrameRecord(
            id = "f-101",
            timestamp = 1700000000000L,
            direction = FrameDirection.CLIENT_TO_SERVER,
            type = FrameType.TEXT,
            length = 12,
            payloadText = "Hello World"
        )

        assertEquals("f-101", record.id)
        assertEquals(1700000000000L, record.timestamp)
        assertEquals(FrameDirection.CLIENT_TO_SERVER, record.direction)
        assertEquals(FrameType.TEXT, record.type)
        assertEquals(12, record.length)
        assertEquals("Hello World", record.payloadText)
        assertNull(record.payloadHex)
    }

    @Test
    fun testCopyAndEquality() {
        val r1 = WebSocketFrameRecord("1", 100L, FrameDirection.CLIENT_TO_SERVER, FrameType.PING, 0)
        val r2 = r1.copy()
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }
}
