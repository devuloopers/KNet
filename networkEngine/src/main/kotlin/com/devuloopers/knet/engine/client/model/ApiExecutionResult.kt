package com.devuloopers.knet.engine.client.model

/**
 * Result data model returned after executing an API call via [KNetApiClient].
 *
 * @param statusCode HTTP response status code (e.g. 200, 404, 500).
 * @param statusText Human-readable status description (e.g. "OK", "Not Found").
 * @param headers Response HTTP headers.
 * @param responseBody Raw string response payload.
 * @param latencyMs Total round-trip execution latency in milliseconds.
 * @param responseSizeBytes Response body byte count.
 * @param isSuccess True if HTTP status code is in 200..299.
 * @param errorMessage Exception message if request execution failed.
 */
data class ApiExecutionResult(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val responseBody: String,
    val latencyMs: Long,
    val responseSizeBytes: Long,
    val isSuccess: Boolean = statusCode in 200..299,
    val errorMessage: String? = null
)
