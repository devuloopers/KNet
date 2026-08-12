package com.devuloopers.knet.ui.desktop.httppanel.model

/**
 * Domain specification for HTTP network timing breakdown and waterfall metrics.
 *
 * @param dnsMs DNS lookup latency in milliseconds.
 * @param tcpMs TCP connection handshake duration in milliseconds.
 * @param tlsMs TLS/SSL handshake duration in milliseconds.
 * @param ttfbMs Time To First Byte (TTFB / wait duration) in milliseconds.
 * @param downloadMs Content download duration in milliseconds.
 * @param totalTimeMs Total request-response roundtrip latency in milliseconds.
 * @param isReusedConnection True if connection pooling reused an existing keep-alive socket.
 */
public data class NetworkTimingSpec(
    val dnsMs: Long = 0L,
    val tcpMs: Long = 0L,
    val tlsMs: Long = 0L,
    val ttfbMs: Long = 0L,
    val downloadMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val isReusedConnection: Boolean = false
)
