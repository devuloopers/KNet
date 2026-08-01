package com.devuloopers.knet.ui.desktop.inspector.model

/**
 * Summary information for captured network transaction analysis.
 */
public data class TransactionOverview(
    val id: String = "",
    val url: String = "",
    val host: String = "",
    val ipAddress: String = "",
    val port: Int = 443,
    val method: String = "GET",
    val statusCode: Int = 200,
    val statusText: String = "OK",
    val protocol: String = "HTTP/1.1",
    val tlsVersion: String = "TLSv1.3",
    val cipherSuite: String = "TLS_AES_256_GCM_SHA384",
    val requestSizeBytes: Long = 0,
    val responseSizeBytes: Long = 0,
    val totalDurationMs: Long = 0,
    val compression: String = "gzip"
)
