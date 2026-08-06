package com.devuloopers.knet.domain.clientNetwork.model

/**
 * Represents an intercepted HTTP request.
 *
 * @property id Unique UUID identifier for tracing.
 * @property method HTTP method (e.g., GET, POST, CONNECT).
 * @property url Complete request URL.
 * @property protocol HTTP protocol version (e.g., HTTP/1.1).
 * @property headers A list of HTTP header name-value pairs.
 * @property body Optional request body payload bytes.
 * @property timestamp Epoch timestamp in milliseconds indicating when the request was captured.
 */
class HttpRequest(
    val id: String,
    val method: String,
    val url: String,
    val protocol: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timestamp: Long
)
