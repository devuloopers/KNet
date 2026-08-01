package com.devuloopers.knet.domain.network.model

/**
 * Represents an intercepted HTTP response corresponding to a request.
 *
 * @property statusCode HTTP status code (e.g., 200, 404).
 * @property statusText HTTP status description (e.g., OK, Not Found).
 * @property headers A list of HTTP header name-value pairs.
 * @property body Optional response body payload bytes.
 * @property timestamp Epoch timestamp in milliseconds indicating when the response was captured.
 */
class HttpResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
    val timestamp: Long
)
