package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import com.devuloopers.knet.scripting.model.ScriptAssertion

/**
 * Single API Studio response-inspection state.
 *
 * @property statusCode HTTP status code (e.g. 200, 404, 500).
 * @property statusText HTTP status textual description (e.g. "OK", "Not Found").
 * @property durationMs Round-trip execution time in milliseconds.
 * @property sizeBytes Total response payload size in bytes.
 * @property mimeType Detected MIME content-type string (e.g. "application/json").
 * @property headers Map of response header name to value.
 * @property cookies Map of response cookie name to value.
 * @property body Formatted raw string content of response payload.
 * @property testResults List of post-response assertion results.
 * @property consoleLogs List of console log output messages generated during script execution.
 * @property failureReason Diagnostic network failure reason if transport error occurred.
 */
data class ResponseInspectorState(
    val statusCode: Int = 0,
    val statusText: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val responseBody: String = "",
    val testResults: List<ScriptAssertion> = emptyList(),
    val consoleLogs: List<String> = emptyList(),
    val failureReason: NetworkFailureReason? = null,
    val errorMessage: String? = null,
    val executionState: ExecutionState = ExecutionState.IDLE,
) {
    val isGatewayError: Boolean
        get() = statusCode == 502 || statusCode == 503 || statusCode == 504

    val hasResponse: Boolean
        get() = (statusCode > 0 || responseBody.isNotBlank()) && !isGatewayError

    val isError: Boolean
        get() = executionState == ExecutionState.ERROR || failureReason != null || isGatewayError ||
            (statusCode == 0 && !errorMessage.isNullOrBlank())
}
