package com.devuloopers.knet.engine.websocket

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WebSocketFrameCodecTest {
    @Test
    fun `incremental decoder preserves masked frame wire bytes`() {
        val maskingKey = byteArrayOf(1, 2, 3, 4)
        val wire = WebSocketFrameDecoder.encode(
            opcode = WebSocketOpcode.TEXT,
            payload = "hello".encodeToByteArray(),
            maskingKey = maskingKey,
        )
        val decoder = WebSocketFrameDecoder(expectsMaskedFrames = true, permitsCompression = false)

        val prefix = assertIs<WebSocketDecodeResult.Frames>(decoder.accept(wire.copyOfRange(0, 3)))
        val suffix = assertIs<WebSocketDecodeResult.Frames>(decoder.accept(wire.copyOfRange(3, wire.size)))

        assertEquals(0, prefix.values.size)
        val frame = suffix.values.single()
        assertEquals(WebSocketOpcode.TEXT, frame.opcode)
        assertContentEquals("hello".encodeToByteArray(), frame.payload)
        assertContentEquals(maskingKey, frame.maskingKey)
        assertContentEquals(wire, frame.originalWireBytes)
    }

    @Test
    fun `decoder supports sixteen and sixty four bit payload lengths`() {
        val medium = ByteArray(126) { it.toByte() }
        val large = ByteArray(70_000) { (it and 0xff).toByte() }
        val decoder = WebSocketFrameDecoder(
            expectsMaskedFrames = false,
            permitsCompression = false,
            maximumFrameBytes = 100_000,
        )

        val decoded = assertIs<WebSocketDecodeResult.Frames>(decoder.accept(
            WebSocketFrameDecoder.encode(WebSocketOpcode.BINARY, medium) +
                WebSocketFrameDecoder.encode(WebSocketOpcode.BINARY, large),
        ))

        assertContentEquals(medium, decoded.values[0].payload)
        assertContentEquals(large, decoded.values[1].payload)
    }

    @Test
    fun `decoder rejects directionally invalid masking`() {
        val serverFrame = WebSocketFrameDecoder.encode(WebSocketOpcode.TEXT, byteArrayOf(1))
        val result = WebSocketFrameDecoder(
            expectsMaskedFrames = true,
            permitsCompression = false,
        ).accept(serverFrame)

        assertEquals("websocket_invalid_masking", assertIs<WebSocketDecodeResult.Failure>(result).errorCode)
    }

    @Test
    fun `decoder rejects compressed control frames even when message compression is negotiated`() {
        val invalidPing = byteArrayOf(0xC9.toByte(), 0x00)

        val result = WebSocketFrameDecoder(
            expectsMaskedFrames = false,
            permitsCompression = true,
        ).accept(invalidPing)

        assertEquals("websocket_invalid_control_frame", assertIs<WebSocketDecodeResult.Failure>(result).errorCode)
        assertFailsWith<IllegalArgumentException> {
            WebSocketFrameDecoder.encode(WebSocketOpcode.PING, byteArrayOf(), compressed = true)
        }
    }

    @Test
    fun `codec rejects close frames with an invalid one byte payload`() {
        val result = WebSocketFrameDecoder(
            expectsMaskedFrames = false,
            permitsCompression = false,
        ).accept(byteArrayOf(0x88.toByte(), 0x01, 0x00))

        assertEquals("websocket_invalid_close_frame", assertIs<WebSocketDecodeResult.Failure>(result).errorCode)
        assertFailsWith<IllegalArgumentException> {
            WebSocketFrameDecoder.encode(WebSocketOpcode.CLOSE, byteArrayOf(0x00))
        }
    }
}
