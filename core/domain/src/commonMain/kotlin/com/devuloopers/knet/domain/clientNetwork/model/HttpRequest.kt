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
 * @property isIntercepted Whether this request was actively intercepted by a breakpoint rule in Netty.
 * @property matchedRuleId Unique ID of the matched breakpoint rule if intercepted.
 */
class HttpRequest(
    val id: String,
    val method: String,
    val url: String,
    val protocol: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timestamp: Long,
    val isIntercepted: Boolean = false,
    val matchedRuleId: String? = null
)
