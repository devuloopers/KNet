package com.devuloopers.knet.engine.proxy.tls

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TlsClientHelloMetadataParserTest {
    @Test
    fun `parser extracts bounded ALPN and supported versions without moving the buffer`() {
        val alpn = byteArrayOf(
            0x00, 0x0c,
            0x02, 'h'.code.toByte(), '2'.code.toByte(),
            0x08, 'h'.code.toByte(), 't'.code.toByte(), 't'.code.toByte(), 'p'.code.toByte(),
            '/'.code.toByte(), '1'.code.toByte(), '.'.code.toByte(), '1'.code.toByte(),
        )
        val supportedVersions = byteArrayOf(0x04, 0x03, 0x04, 0x03, 0x03)
        val extensions = extension(16, alpn) + extension(43, supportedVersions)
        val hello = Unpooled.buffer().apply {
            writeShort(0x0303)
            writeZero(32)
            writeByte(0)
            writeShort(2)
            writeShort(0x1301)
            writeByte(1)
            writeByte(0)
            writeShort(extensions.size)
            writeBytes(extensions)
        }
        val readerIndex = hello.readerIndex()

        val metadata = TlsClientHelloMetadataParser.parse(hello, "video.example.test")

        assertEquals(readerIndex, hello.readerIndex())
        assertEquals("video.example.test", metadata.serverName)
        assertEquals(listOf("h2", "http/1.1"), metadata.offeredApplicationProtocols)
        assertEquals(listOf("TLS 1.3", "TLS 1.2"), metadata.offeredTlsVersions)
        hello.release()
    }

    @Test
    fun `parser returns empty bounded metadata for truncated extensions`() {
        val hello = Unpooled.wrappedBuffer(byteArrayOf(0x03, 0x03, 0x00))

        val metadata = TlsClientHelloMetadataParser.parse(hello)

        assertTrue(metadata.offeredApplicationProtocols.isEmpty())
        assertTrue(metadata.offeredTlsVersions.isEmpty())
        hello.release()
    }

    private fun extension(type: Int, payload: ByteArray): ByteArray =
        byteArrayOf(
            (type ushr 8).toByte(),
            type.toByte(),
            (payload.size ushr 8).toByte(),
            payload.size.toByte(),
        ) + payload
}
