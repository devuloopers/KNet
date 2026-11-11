package com.devuloopers.knet.engine.proxy.tls

import io.netty.buffer.ByteBuf

/** Whether a ClientHello can reuse authenticated state without transmitting a new server certificate. */
enum class TlsResumptionOffer {
    /** A complete parsed hello contains no session-resumption offer. */
    NONE,
    /** A legacy session/ticket or TLS 1.3 PSK is offered. */
    OFFERED,
    /** Incomplete or unsupported metadata cannot exclude resumption. */
    UNKNOWN,
}

/** Bounded, payload-free metadata parsed from one complete TLS ClientHello. */
data class TlsClientHelloMetadata(
    /** Normalized SNI exposed by Netty, when present. */
    val serverName: String?,
    /** Offered ALPN tokens in client preference order. */
    val offeredApplicationProtocols: List<String>,
    /** Offered TLS versions in client preference order. */
    val offeredTlsVersions: List<String>,
    /** Never treat a retained session certificate as evidence of a newly transmitted certificate. */
    val resumptionOffer: TlsResumptionOffer = TlsResumptionOffer.UNKNOWN,
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
        if (!skip(compressionMethodsLength)) return empty(serverName)
        if (cursor == end) {
            return TlsClientHelloMetadata(serverName, emptyList(), listOf(versionLabel(legacyVersion)),
                if (sessionIdLength > 0) TlsResumptionOffer.OFFERED else TlsResumptionOffer.NONE)
        }
        val extensionsLength = unsignedShort() ?: return empty(serverName)
        val extensionsEnd = cursor + extensionsLength
        if (extensionsEnd < cursor || extensionsEnd > end) return empty(serverName)
        val protocols = mutableListOf<String>()
        val versions = mutableListOf<String>()
        var sessionOffered = false
        var complete = extensionsEnd == end
        while (cursor <= extensionsEnd - EXTENSION_HEADER_BYTES) {
            val type = unsignedShort() ?: break
            val length = unsignedShort() ?: break
            val extensionEnd = cursor + length
            if (extensionEnd < cursor || extensionEnd > extensionsEnd) {
                complete = false
                break
            }
            when (type) {
                ALPN_EXTENSION -> parseAlpn(clientHello, cursor, extensionEnd, protocols)
                SUPPORTED_VERSIONS_EXTENSION -> parseVersions(clientHello, cursor, extensionEnd, versions)
                PRE_SHARED_KEY_EXTENSION -> sessionOffered = true
                SESSION_TICKET_EXTENSION -> if (length > 0) sessionOffered = true
            }
            cursor = extensionEnd
        }
        complete = complete && cursor == extensionsEnd
        // TLS 1.3 uses a non-empty compatibility session ID even on a new full handshake.
        if (versionLabel(TLS_13_VERSION) !in versions && sessionIdLength > 0) sessionOffered = true
        if (versions.isEmpty()) versions += versionLabel(legacyVersion)
        return TlsClientHelloMetadata(
            serverName = serverName,
            offeredApplicationProtocols = protocols.distinct().take(MAXIMUM_RETAINED_VALUES),
            offeredTlsVersions = versions.distinct().take(MAXIMUM_RETAINED_VALUES),
            resumptionOffer = when {
                !complete -> TlsResumptionOffer.UNKNOWN
                sessionOffered -> TlsResumptionOffer.OFFERED
                else -> TlsResumptionOffer.NONE
            },
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
    private const val PRE_SHARED_KEY_EXTENSION: Int = 41
    private const val SESSION_TICKET_EXTENSION: Int = 35
    private const val TLS_13_VERSION: Int = 0x0304
    private const val MAXIMUM_RETAINED_VALUES: Int = 16
    private val PRINTABLE_ASCII_RANGE: IntRange = 0x21..0x7e
}
