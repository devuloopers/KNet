package com.devuloopers.knet.engine.proxy.tls

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import kotlin.test.*

/** Outgoing plaintext evidence distinguishes full handshakes from PSK/session reuse without reading certificate DER. */
class TlsServerFlightMetadataTest {
    @Test fun `TLS 13 certificate authentication requires a complete non PSK ServerHello`() {
        val metadata = TlsServerFlightMetadata()
        hello(version = 0x0304).use { buffer ->
            val index = buffer.readerIndex(); metadata.observe(buffer)
            assertEquals(index, buffer.readerIndex())
        }
        assertTrue(metadata.certificateAuthentication)
    }

    @Test fun `selected TLS 13 PSK never treats retained certificates or encrypted bytes as a fresh certificate`() {
        val metadata = TlsServerFlightMetadata()
        hello(version = 0x0304, psk = true).use(metadata::observe)
        record(23, handshake(11, certificate())).use(metadata::observe)
        assertFalse(metadata.certificateAuthentication)
    }

    @Test fun `HelloRetryRequest and compatibility CCS do not prove a certificate but the following full hello can`() {
        val metadata = TlsServerFlightMetadata()
        hello(version = 0x0304, retry = true).use(metadata::observe)
        record(20, byteArrayOf(1)).use(metadata::observe)
        assertFalse(metadata.certificateAuthentication)
        hello(version = 0x0304).use(metadata::observe)
        assertTrue(metadata.certificateAuthentication)
    }

    @Test fun `TLS 12 needs an actual plaintext certificate message before encryption begins`() {
        val metadata = TlsServerFlightMetadata()
        hello(version = 0x0303).use(metadata::observe)
        assertFalse(metadata.certificateAuthentication)
        record(22, handshake(11, certificate())).use(metadata::observe)
        assertTrue(metadata.certificateAuthentication)
        val resumed = TlsServerFlightMetadata()
        hello(version = 0x0303).use(resumed::observe)
        record(20, byteArrayOf(1)).use(resumed::observe)
        record(22, handshake(11, certificate())).use(resumed::observe)
        assertFalse(resumed.certificateAuthentication, "Ciphertext after CCS cannot be parsed as a certificate header")
    }

    @Test fun `malformed duplicate and partial metadata remain inconclusive without retained reassembly buffers`() {
        hello(version = 0x0304, duplicateVersion = true).use { buffer ->
            val metadata = TlsServerFlightMetadata(); metadata.observe(buffer); assertFalse(metadata.certificateAuthentication)
        }
        hello(version = 0x0304).use { buffer ->
            val metadata = TlsServerFlightMetadata()
            metadata.observe(buffer.slice(0, buffer.readableBytes() - 1))
            metadata.observe(buffer)
            assertFalse(metadata.certificateAuthentication)
        }
        record(23, handshake(2, ByteArray(64))).use { buffer ->
            val metadata = TlsServerFlightMetadata(); metadata.observe(buffer); assertFalse(metadata.certificateAuthentication)
        }
    }

    private fun certificate() = byteArrayOf(0, 0, 4, 0, 0, 1, 0) // Only list/header lengths matter; DER is never read.
    private fun hello(version: Int, psk: Boolean = false, retry: Boolean = false, duplicateVersion: Boolean = false): ByteBuf {
        val body = Unpooled.buffer()
        val extensions = Unpooled.buffer()
        try {
            body.writeShort(0x0303)
            if (retry) body.writeBytes("cf21ad74e59a6111be1d8c021e65b891c2a211167abb8c5e079e09e2c8a8339c".chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            else body.writeZero(32)
            body.writeByte(0).writeShort(if (version == 0x0304) 0x1301 else 0xc02f).writeByte(0)
            if (version == 0x0304) {
                extensions.writeShort(43).writeShort(2).writeShort(version)
                if (duplicateVersion) extensions.writeShort(43).writeShort(2).writeShort(version)
                if (psk) extensions.writeShort(41).writeShort(2).writeShort(0)
                body.writeShort(extensions.readableBytes()).writeBytes(extensions)
            }
            val bytes = ByteArray(body.readableBytes()); body.readBytes(bytes)
            return record(22, handshake(2, bytes))
        } finally { body.release(); extensions.release() }
    }
    private fun handshake(type: Int, body: ByteArray): ByteArray {
        val buffer = Unpooled.buffer().writeByte(type).writeMedium(body.size).writeBytes(body)
        return try { ByteArray(buffer.readableBytes()).also(buffer::readBytes) } finally { buffer.release() }
    }
    private fun record(type: Int, body: ByteArray): ByteBuf = Unpooled.buffer().writeByte(type).writeShort(0x0303).writeShort(body.size).writeBytes(body)
    private inline fun ByteBuf.use(block: (ByteBuf) -> Unit) { try { block(this) } finally { release() } }
}
