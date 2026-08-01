package com.devuloopers.knet.engine.protocol

import com.devuloopers.knet.engine.protocol.grpc.ProtobufDynamicDecoder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProtobufDynamicDecoderTest {

    @Test
    fun testUnknownMessageTypeReturnsErrorJson() {
        val decoder = ProtobufDynamicDecoder()
        val result = decoder.decodeToJson("com.example.UnknownMessage", byteArrayOf(0x08, 0x96.toByte(), 0x01))

        assertNotNull(result)
        assertTrue(result.contains("Unknown message type"))
    }
}
