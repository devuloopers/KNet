package com.devuloopers.knet.ui.desktop.apistudio.model

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
    val body: String = ""
)
