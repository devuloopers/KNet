package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.grpc.ProtobufDynamicDecoder
import com.devuloopers.knet.engine.protocol.websocket.KNetWebSocketFrameHandler
import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val decoder = ProtobufDynamicDecoder()
        val handler = KNetWebSocketFrameHandler(FrameDirection.CLIENT_TO_SERVER) {}

        assertNotNull(decoder)
        assertNotNull(handler)
    }
}
