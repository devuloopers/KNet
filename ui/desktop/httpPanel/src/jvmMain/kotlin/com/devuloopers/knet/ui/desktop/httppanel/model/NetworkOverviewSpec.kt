package com.devuloopers.knet.ui.desktop.httppanel.model

/**
 * Domain specification for HTTP transaction overview inspection.
 *
 * @param method HTTP method (e.g. "GET", "POST").
 * @param url Target endpoint URL string.
 * @param statusCode HTTP status code (e.g. 200, 404, 500).
 * @param statusText HTTP status phrase or error detail text.
 * @param isTerminal Whether capture observed a final outcome even when timing is unavailable.
 * @param clientProtocol Protocol observed on the inspected client-to-KNet request leg.
 * @param upstreamProtocol Protocol observed on the upstream response leg, when available.
 * @param origin Human-readable feature or client that initiated the exchange.
 * @param connectionId Stable transport connection identifier, when captured.
 * @param streamId Multiplexed stream identifier, when captured.
 * @param remoteIp Remote host IP address and port (e.g. "127.0.0.1:443").
 * @param timestamp Transaction timestamp string.
 * @param durationMs Formatted duration string (e.g. "45 ms").
 * @param sizeBytes Formatted byte size string (e.g. "2.4 KB").
 * @param contentType Response Content-Type header string (e.g. "application/json").
 */
data class NetworkOverviewSpec(
    val method: String,
    val url: String,
    val statusCode: Int,
    val statusText: String,
    val isTerminal: Boolean = false,
    val clientProtocol: String = "HTTP/1.1",
    val upstreamProtocol: String? = null,
    val origin: String = "Proxy client",
    val connectionId: String? = null,
    val streamId: Long? = null,
    val remoteIp: String = "",
    val timestamp: String = "",
    val durationMs: String = "",
    val sizeBytes: String = "",
    val contentType: String = ""
)
