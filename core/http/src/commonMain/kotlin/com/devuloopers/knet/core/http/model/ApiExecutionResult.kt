package com.devuloopers.knet.core.http.model

/**
 * Result data class returned after executing an HTTP request.
 *
 * @property statusCode HTTP response status code (e.g., 200, 404, 500).
 * @property statusText HTTP response status description (e.g., "OK", "Not Found").
 * @property headers Map of HTTP response header name-value pairs.
 * @property responseBody Formatted response payload string.
 * @property latencyMs Total duration of request execution in milliseconds.
 * @property responseSizeBytes Size of response payload content in bytes.
 * @property isSuccess True if status code is in 2xx range; false otherwise.
 * @property errorMessage Error description if execution failed before completion.
 * @property metrics Fine-grained network socket timing metrics.
 */
data class ApiExecutionResult(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val responseBody: String,
    val latencyMs: Long,
    val responseSizeBytes: Long,
    val isSuccess: Boolean = statusCode in 200..299,
    val errorMessage: String? = null,
    val metrics: HttpMetrics = HttpMetrics(totalTimeMs = latencyMs)
)
