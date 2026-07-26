package com.devuloopers.knet.model

/**
 * Value object encapsulating network socket connection phase timing metrics for an HTTP transaction.
 *
 * @property dnsMs Duration of DNS resolution in milliseconds.
 * @property tcpMs Duration of TCP handshake in milliseconds.
 * @property tlsMs Duration of TLS/SSL handshake in milliseconds.
 * @property ttfbMs Time to First Byte (server response latency) in milliseconds.
 * @property downloadMs Duration of response payload content download in milliseconds.
 */
data class HttpTimings(
    val dnsMs: Long = 0L,
    val tcpMs: Long = 0L,
    val tlsMs: Long = 0L,
    val ttfbMs: Long = 0L,
    val downloadMs: Long = 0L
) {
    /** Combined total time across all timing phases. */
    val totalTimeMs: Long
        get() = dnsMs + tcpMs + tlsMs + ttfbMs + downloadMs

    /** Returns true if high-resolution socket timing metrics are present. */
    val hasRealTimings: Boolean
        get() = tcpMs > 0L || ttfbMs > 0L
}
