package com.devuloopers.knet.ui.desktop.apistudio.model

import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason

/**
 * Represents a single test assertion result (e.g. from post-response script).
 *
 * @property name Test assertion title.
 * @property passed True if assertion succeeded.
 * @property errorMessage Optional error detail string if assertion failed.
 */
public data class TestResult(
    val name: String,
    val passed: Boolean,
    val errorMessage: String? = null
)

/**
 * Presentation model formatting HTTP responses for UI preview.
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
public data class ResponsePresentation(
    val statusCode: Int = 200,
    val statusText: String = "OK",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val mimeType: String = "application/json",
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val body: String = "",
    val testResults: List<TestResult> = emptyList(),
    val consoleLogs: List<String> = emptyList(),
    val failureReason: NetworkFailureReason? = null
)
