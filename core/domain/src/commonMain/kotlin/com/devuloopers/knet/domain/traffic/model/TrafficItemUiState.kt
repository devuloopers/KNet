package com.devuloopers.knet.domain.traffic.model

import com.devuloopers.knet.domain.network.model.HttpTimings

/**
 * Immutable pre-calculated display state model for a single traffic feed row.
 *
 * @property id Sequential numerical identifier for UI display.
 * @property transactionId Unique UUID string of the underlying transaction.
 * @property method HTTP method (e.g. GET, POST, CONNECT, WS).
 * @property host Target host domain.
 * @property path URI path string.
 * @property status HTTP status code (e.g. 200, 404, 0 for active).
 * @property statusText Description of status code.
 * @property protocol Transport/Application protocol string (e.g. HTTP/1.1, HTTP/2, HTTPS, WS).
 * @property formattedTime Display duration string (e.g. "120 ms").
 * @property formattedSize Display byte size (e.g. "1.2 KB").
 * @property dateGroup Formatted date header string.
 * @property requestBody Formatted request payload text.
 * @property responseBody Formatted response payload text.
 * @property queryParams Parsed query parameters map.
 * @property requestHeaders Request headers map.
 * @property responseHeaders Response headers map.
 * @property isSelected Whether this row is currently selected by the user.
 */
data class TrafficItemUiState(
    val id: Int,
    val transactionId: String,
    val method: String,
    val host: String,
    val path: String,
    val status: Int,
    val statusText: String,
    val protocol: String = "HTTP/2",
    val formattedTime: String,
    val formattedSize: String,
    val dateGroup: String,
    val requestBody: String,
    val responseBody: String,
    val queryParams: Map<String, Any>,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val timings: HttpTimings = HttpTimings(),
    val isSelected: Boolean = false
)
