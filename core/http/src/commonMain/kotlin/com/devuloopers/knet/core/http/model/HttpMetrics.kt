package com.devuloopers.knet.core.http.model

/**
 * Value object encapsulating network socket connection phase timing metrics for an HTTP transaction.
 *
 * @property totalTimeMs Combined total time across all timing phases in milliseconds.
 * @property dnsTimeMs Duration of DNS resolution in milliseconds, or null if direct.
 * @property tcpTimeMs Duration of TCP handshake in milliseconds, or null.
 * @property tlsTimeMs Duration of TLS/SSL handshake in milliseconds, or null.
 * @property ttfbTimeMs Time to First Byte (server response latency) in milliseconds, or null.
 * @property downloadTimeMs Duration of response payload content download in milliseconds, or null.
 */
data class HttpMetrics(
    val totalTimeMs: Long = 0L,
    val dnsTimeMs: Long? = null,
    val tcpTimeMs: Long? = null,
    val tlsTimeMs: Long? = null,
    val ttfbTimeMs: Long? = null,
    val downloadTimeMs: Long? = null
)
