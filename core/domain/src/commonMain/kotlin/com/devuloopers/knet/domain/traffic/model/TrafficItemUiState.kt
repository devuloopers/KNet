package com.devuloopers.knet.domain.traffic.model

import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.protocol.model.InterceptionMetadata

/**
 * Immutable pre-calculated display state model for a single traffic feed row.
 *
 * @property id Sequential numerical identifier for UI display.
 * @property transactionId Unique UUID string of the underlying transaction.
 * @property method HTTP method (e.g. GET, POST, CONNECT, WS).
 * @property scheme Target URL scheme (e.g. "http", "https").
 * @property host Target host domain.
 * @property path URI path string.
 * @property status HTTP status code (e.g. 200, 404, 0 for active).
 * @property statusText Description of status code.
 * @property protocol Transport/Application protocol string (e.g. HTTP/1.1, HTTP/2, HTTPS, WS).
 * @property timestamp Epoch millisecond timestamp when request was initiated.
 * @property formattedTimestamp Formatted start timestamp string (e.g. "19:28:35" or "14:05:12 - 10/08").
 * @property formattedTime Display duration string (e.g. "120 ms").
 * @property formattedSize Display byte size (e.g. "1.2 KB").
 * @property dateGroup Formatted date header string.
 * @property requestBody Formatted request payload text.
 * @property responseBody Formatted response payload text.
 * @property queryParams Parsed query parameters map.
 * @property requestHeaders Request headers map.
 * @property responseHeaders Response headers map.
 * @property interceptionMetadata Protocol metadata detected during network interception.
 * @property isIntercepted Whether Netty proxy engine actively intercepted this transaction.
 * @property matchedRuleId ID of the matched breakpoint rule if intercepted.
 * @property isSelected Whether this row is currently selected by the user.
 */
data class TrafficItemUiState(
    val id: Int,
    val transactionId: String,
    val method: String,
    val scheme: String = "http",
    val host: String,
    val path: String,
    val status: Int,
    val statusText: String,
    val protocol: String = "HTTP/2",
    val timestamp: Long = 0L,
    val formattedTimestamp: String = "",
    val formattedTime: String,
    val formattedSize: String,
    val dateGroup: String,
    val requestBody: String,
    val responseBody: String,
    val queryParams: Map<String, Any>,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val timings: HttpTimings = HttpTimings(),
    val interceptionMetadata: InterceptionMetadata = InterceptionMetadata.GenericHttp,
    val isIntercepted: Boolean = false,
    val matchedRuleId: String? = null,
    val isSelected: Boolean = false
) {
    /**
     * Reconstructed absolute target URL preserving original scheme (https vs http).
     */
    val fullUrl: String
        get() {
            if (path.startsWith("http://") || path.startsWith("https://")) return path
            val activeScheme = if (scheme.isNotBlank()) scheme else if (protocol.contains("HTTPS", ignoreCase = true)) "https" else "http"
            return if (host.isNotBlank()) "$activeScheme://$host$path" else path
        }
}
