package com.devuloopers.knet.ui.desktop.httppanel.model

/**
 * Domain specification for HTTP transaction overview inspection.
 *
 * @param method HTTP method (e.g. "GET", "POST").
 * @param url Target endpoint URL string.
 * @param statusCode HTTP status code (e.g. 200, 404, 500).
 * @param statusText HTTP status phrase or error detail text.
 * @param protocol HTTP protocol version string (e.g. "HTTP/1.1", "HTTP/2").
 * @param remoteIp Remote host IP address and port (e.g. "127.0.0.1:443").
 * @param timestamp Transaction timestamp string.
 * @param durationMs Formatted duration string (e.g. "45 ms").
 * @param sizeBytes Formatted byte size string (e.g. "2.4 KB").
 * @param contentType Response Content-Type header string (e.g. "application/json").
 */
public data class NetworkOverviewSpec(
    val method: String,
    val url: String,
    val statusCode: Int,
    val statusText: String,
    val protocol: String = "HTTP/1.1",
    val remoteIp: String = "",
    val timestamp: String = "",
    val durationMs: String = "",
    val sizeBytes: String = "",
    val contentType: String = ""
)
