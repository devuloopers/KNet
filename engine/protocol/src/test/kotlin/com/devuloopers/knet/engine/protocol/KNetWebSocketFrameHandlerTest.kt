package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.websocket.KNetWebSocketFrameHandler
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KNetWebSocketFrameHandlerTest {

    @Test
    fun testInboundAndOutboundFrameInterception() {
        val records = mutableListOf<WebSocketFrameRecord>()
        val handler = KNetWebSocketFrameHandler(FrameDirection.CLIENT_TO_SERVER) { record ->
            records.add(record)
        }
        val channel = EmbeddedChannel(handler)

        val inboundFrame = TestFixtures.createTextWebSocketFrame("Inbound text")
        channel.writeInbound(inboundFrame)

        assertEquals(1, records.size)
        assertEquals(FrameDirection.CLIENT_TO_SERVER, records[0].direction)
        assertEquals("Inbound text", records[0].payloadText)

        val outboundFrame = TestFixtures.createBinaryWebSocketFrame(byteArrayOf(0xFF.toByte()))
        channel.writeOutbound(outboundFrame)

        assertEquals(2, records.size)
        assertEquals(FrameDirection.SERVER_TO_CLIENT, records[1].direction)
        assertEquals("FF", records[1].payloadHex)

        val closeFrame = TestFixtures.createCloseWebSocketFrame()
        channel.writeInbound(closeFrame)

        assertEquals(3, records.size)
        assertEquals(FrameType.CLOSE, records[2].type)
        assertTrue(records[2].payloadText!!.contains("Normal Closure"))
    }
}
