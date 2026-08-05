package com.devuloopers.knet.ui.desktop.apistudio.model

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
    val consoleLogs: List<String> = emptyList()
)
