package com.devuloopers.knet.domain.network.model

import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason

/**
 * Strongly-typed domain contract representing an HTTP response specification across KNet.
 *
 * Owned centrally by `:core:domain` to fuel Response Inspector views in Live Traffic,
 * API Studio execution output, and live interception response viewers.
 *
 * @property statusCode HTTP status code (e.g. 200, 404, 500, 0 for active/failed).
 * @property statusText Description of the HTTP status code (e.g. "OK", "Not Found").
 * @property durationMs Round-trip execution latency in milliseconds.
 * @property sizeBytes Response body size in bytes.
 * @property responseBody Decoded text payload of the HTTP response.
 * @property headers Preserved list of HTTP response header key-value pairs.
 * @property cookies List of parsed response cookie key-value pairs.
 * @property failureReason Strongly-typed network failure classification if transport failed.
 * @property errorMessage Descriptive diagnostic error text if execution failed.
 */
data class NetworkResponseSpec(
    val statusCode: Int = 0,
    val statusText: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val responseBody: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val cookies: List<Pair<String, String>> = emptyList(),
    val failureReason: NetworkFailureReason? = null,
    val errorMessage: String? = null
) {
    /** True if HTTP status code indicates a 5xx gateway/proxy transport error. */
    val isGatewayError: Boolean get() = statusCode == 502 || statusCode == 503 || statusCode == 504

    /** True if a valid HTTP response or response body was received. */
    val hasResponse: Boolean get() = (statusCode > 0 || responseBody.isNotBlank()) && !isGatewayError

    /** True if an error occurred during transport or validation. */
    val isError: Boolean get() = failureReason != null || isGatewayError || (statusCode == 0 && !errorMessage.isNullOrBlank())
}
