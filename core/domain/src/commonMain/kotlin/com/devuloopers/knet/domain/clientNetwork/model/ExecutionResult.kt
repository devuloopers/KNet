package com.devuloopers.knet.domain.clientNetwork.model

import com.devuloopers.knet.traffic.model.ExchangeTimings

/**
 * Domain result representation returned after executing a client HTTP/HTTPS API request.
 *
 * @property statusCode HTTP status code (e.g., 200, 404, 500).
 * @property statusText HTTP status description (e.g., "OK", "Not Found").
 * @property headers Map of HTTP response header name-value pairs.
 * @property cookies Map of active response cookies for host domain.
 * @property responseBody Formatted or raw response payload string.
 * @property timings Canonical network phase timings shared with recorded traffic.
 * @property responseSizeBytes Payload size in bytes.
 * @property isSuccess True if status code is in 2xx range; false otherwise.
 * @property errorMessage Optional failure description if execution throws an exception.
 * @property failureReason Strongly-typed network execution failure category, or null on success.
 */
data class ExecutionResult(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val responseBody: String = "",
    val timings: ExchangeTimings = ExchangeTimings(),
    val responseSizeBytes: Long = 0L,
    val isSuccess: Boolean = statusCode in 200..299,
    val errorMessage: String? = null,
    val failureReason: NetworkFailureReason? = null
) {
    /** Compatibility-free convenience derived from the canonical total timing. */
    val latencyMs: Long
        get() = timings.totalMillis ?: 0L
}
