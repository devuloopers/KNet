package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.websocket.KNetWebSocketFrameHandler
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolIntegrationTest {

    @Test
    fun testEmbeddedChannelPipelineInterception() {
        val captured = mutableListOf<WebSocketFrameRecord>()
        val handler = KNetWebSocketFrameHandler(FrameDirection.CLIENT_TO_SERVER) { frame ->
            captured.add(frame)
        }
        val channel = EmbeddedChannel(handler)

        val frame = TestFixtures.createTextWebSocketFrame("ping")
        channel.writeInbound(frame)

        assertEquals(1, captured.size)
        assertEquals("ping", captured[0].payloadText)
    }
}
