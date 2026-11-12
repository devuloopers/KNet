package com.devuloopers.knet.engine.proxy.tls

import io.netty.buffer.ByteBuf

/**
 * Zero-copy evidence from complete plaintext server handshake records, never certificate DER or encrypted payload.
 * TLS 1.2 exposes its Certificate message header. TLS 1.3 selects certificate authentication when a real
 * ServerHello selects version 1.3 without pre_shared_key (RFC 8446 sections 4.1.3 and 4.2.11).
 * Partial/unsupported records remain inconclusive; the audited JSSE flight/write checks are still required.
 */
internal class TlsServerFlightMetadata {
    /** Whether the current server flight independently establishes full certificate authentication. */
    var certificateAuthentication: Boolean = false
        private set
    private var finished = false
    private var selectedVersion: Int? = null

    /** Inspects bounded record/header metadata without changing buffer indices, copying bytes or retaining buffers. */
    fun observe(buffer: ByteBuf) {
        if (finished) return
        var cursor = buffer.readerIndex()
        val end = cursor + minOf(buffer.readableBytes(), 65_536)
        while (cursor < end && !finished) {
            if (end - cursor < 5) { finished = true; return }
            val type = buffer.getUnsignedByte(cursor).toInt()
            val length = buffer.getUnsignedShort(cursor + 3)
            if (length > end - cursor - 5) { finished = true; return }
            val recordEnd = cursor + 5 + length
            when (type) {
                20 -> if (selectedVersion != 0x0304) finished = true // TLS 1.2 ciphertext starts after CCS.
                22 -> observeHandshake(buffer, cursor + 5, recordEnd)
                23 -> Unit // TLS 1.3 ciphertext is never parsed as handshake metadata.
                else -> finished = true
            }
            cursor = recordEnd
        }
    }

    private fun observeHandshake(buffer: ByteBuf, start: Int, end: Int) {
        var cursor = start
        while (cursor < end && !finished) {
            if (end - cursor < 4) { finished = true; return }
            val type = buffer.getUnsignedByte(cursor).toInt()
            val length = buffer.getUnsignedMedium(cursor + 1)
            val body = cursor + 4
            if (length > end - body) { finished = true; return }
            val messageEnd = body + length
            when (type) {
                2 -> observeServerHello(buffer, body, messageEnd)
                11 -> if (selectedVersion == 0x0303 && length > 3 && buffer.getUnsignedMedium(body) == length - 3) {
                    certificateAuthentication = true
                    finished = true
                }
            }
            cursor = messageEnd
        }
    }

    private fun observeServerHello(buffer: ByteBuf, start: Int, end: Int) {
        if (end - start < 38 || buffer.getUnsignedShort(start) != 0x0303) { finished = true; return }
        val retry = HELLO_RETRY_RANDOM.indices.all { buffer.getByte(start + 2 + it) == HELLO_RETRY_RANDOM[it] }
        val sessionBytes = buffer.getUnsignedByte(start + 34).toInt()
        if (sessionBytes > 32 || sessionBytes > end - start - 38) { finished = true; return }
        var cursor = start + 35 + sessionBytes + 3 // Session ID, cipher suite and compression.
        if (cursor > end) { finished = true; return }
        var version = 0x0303
        var psk = false
        var versionSeen = false
        if (cursor < end) {
            if (end - cursor < 2) { finished = true; return }
            val extensionsLength = buffer.getUnsignedShort(cursor)
            cursor += 2
            if (extensionsLength != end - cursor) { finished = true; return }
            while (cursor < end) {
                if (end - cursor < 4) { finished = true; return }
                val type = buffer.getUnsignedShort(cursor)
                val length = buffer.getUnsignedShort(cursor + 2)
                cursor += 4
                if (length > end - cursor) { finished = true; return }
                when (type) {
                    43 -> {
                        if (versionSeen || length != 2) { finished = true; return }
                        versionSeen = true
                        version = buffer.getUnsignedShort(cursor)
                    }
                    41 -> {
                        if (psk || length != 2) { finished = true; return }
                        psk = true
                    }
                }
                cursor += length
            }
        }
        selectedVersion = version
        if (retry) return // HelloRetryRequest itself sends no certificate and may precede compatibility CCS.
        if (version == 0x0304) {
            certificateAuthentication = !psk
            finished = true
        }
    }

    private companion object {
        // RFC 8446 section 4.1.3's fixed HelloRetryRequest random, not retained connection data.
        val HELLO_RETRY_RANDOM = "cf21ad74e59a6111be1d8c021e65b891c2a211167abb8c5e079e09e2c8a8339c"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
