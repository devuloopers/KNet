package com.devuloopers.knet.engine.proxy.tls

import io.netty.buffer.ByteBuf

/** Bounded, payload-free metadata parsed from one complete TLS ClientHello. */
data class TlsClientHelloMetadata(
    /** Normalized SNI exposed by Netty, when present. */
    val serverName: String?,
    /** Offered ALPN tokens in client preference order. */
    val offeredApplicationProtocols: List<String>,
    /** Offered TLS versions in client preference order. */
    val offeredTlsVersions: List<String>,
)

/** Strict bounds-checked parser for safe ClientHello extension metadata. */
internal object TlsClientHelloMetadataParser {
    /** Parses the ClientHello body supplied by Netty without changing its reader index. */
    fun parse(clientHello: ByteBuf, serverName: String? = null): TlsClientHelloMetadata {
        val start = clientHello.readerIndex()
        val end = clientHello.writerIndex()
        var cursor = start
        fun requireBytes(count: Int): Boolean = count >= 0 && cursor <= end - count
        fun unsignedByte(): Int? = if (requireBytes(1)) clientHello.getUnsignedByte(cursor++).toInt() else null
        fun unsignedShort(): Int? = if (requireBytes(2)) {
            clientHello.getUnsignedShort(cursor).toInt().also { cursor += 2 }
        } else {
            null
        }
        fun skip(count: Int): Boolean = requireBytes(count).also { valid -> if (valid) cursor += count }

        val legacyVersion = unsignedShort() ?: return empty(serverName)
        if (!skip(CLIENT_RANDOM_BYTES)) return empty(serverName)
        val sessionIdLength = unsignedByte() ?: return empty(serverName)
        if (!skip(sessionIdLength)) return empty(serverName)
        val cipherSuitesLength = unsignedShort() ?: return empty(serverName)
        if (cipherSuitesLength % 2 != 0 || !skip(cipherSuitesLength)) return empty(serverName)
        val compressionMethodsLength = unsignedByte() ?: return empty(serverName)
        if (!skip(compressionMethodsLength) || cursor == end) {
            return TlsClientHelloMetadata(serverName, emptyList(), listOf(versionLabel(legacyVersion)))
        }
        val extensionsLength = unsignedShort() ?: return empty(serverName)
        val extensionsEnd = cursor + extensionsLength
        if (extensionsEnd < cursor || extensionsEnd > end) return empty(serverName)
        val protocols = mutableListOf<String>()
        val versions = mutableListOf<String>()
        while (cursor <= extensionsEnd - EXTENSION_HEADER_BYTES) {
            val type = unsignedShort() ?: break
            val length = unsignedShort() ?: break
            val extensionEnd = cursor + length
            if (extensionEnd < cursor || extensionEnd > extensionsEnd) break
            when (type) {
                ALPN_EXTENSION -> parseAlpn(clientHello, cursor, extensionEnd, protocols)
                SUPPORTED_VERSIONS_EXTENSION -> parseVersions(clientHello, cursor, extensionEnd, versions)
            }
            cursor = extensionEnd
        }
        if (versions.isEmpty()) versions += versionLabel(legacyVersion)
        return TlsClientHelloMetadata(
            serverName = serverName,
            offeredApplicationProtocols = protocols.distinct().take(MAXIMUM_RETAINED_VALUES),
            offeredTlsVersions = versions.distinct().take(MAXIMUM_RETAINED_VALUES),
        )
    }

    private fun parseAlpn(buffer: ByteBuf, start: Int, end: Int, output: MutableList<String>) {
        if (end - start < 2) return
        var cursor = start
        val listLength = buffer.getUnsignedShort(cursor).toInt()
        cursor += 2
        val listEnd = cursor + listLength
        if (listEnd > end || listEnd < cursor) return
        while (cursor < listEnd && output.size < MAXIMUM_RETAINED_VALUES) {
            val length = buffer.getUnsignedByte(cursor).toInt()
            cursor += 1
            if (length == 0 || cursor > listEnd - length) return
            val value = buffer.toString(cursor, length, Charsets.US_ASCII)
            if (value.all { character -> character.code in PRINTABLE_ASCII_RANGE }) output += value
            cursor += length
        }
    }

    private fun parseVersions(buffer: ByteBuf, start: Int, end: Int, output: MutableList<String>) {
        if (end - start < 1) return
        var cursor = start
        val listLength = buffer.getUnsignedByte(cursor).toInt()
        cursor += 1
        val listEnd = cursor + listLength
        if (listLength % 2 != 0 || listEnd > end || listEnd < cursor) return
        while (cursor <= listEnd - 2 && output.size < MAXIMUM_RETAINED_VALUES) {
            output += versionLabel(buffer.getUnsignedShort(cursor).toInt())
            cursor += 2
        }
    }

    private fun empty(serverName: String?): TlsClientHelloMetadata =
        TlsClientHelloMetadata(serverName, emptyList(), emptyList())

    private fun versionLabel(value: Int): String = when (value) {
        0x0301 -> "TLS 1.0"
        0x0302 -> "TLS 1.1"
        0x0303 -> "TLS 1.2"
        0x0304 -> "TLS 1.3"
        else -> "0x${value.toString(16).padStart(4, '0')}"
    }

    private const val CLIENT_RANDOM_BYTES: Int = 32
    private const val EXTENSION_HEADER_BYTES: Int = 4
    private const val ALPN_EXTENSION: Int = 16
    private const val SUPPORTED_VERSIONS_EXTENSION: Int = 43
    private const val MAXIMUM_RETAINED_VALUES: Int = 16
    private val PRINTABLE_ASCII_RANGE: IntRange = 0x21..0x7e
}
