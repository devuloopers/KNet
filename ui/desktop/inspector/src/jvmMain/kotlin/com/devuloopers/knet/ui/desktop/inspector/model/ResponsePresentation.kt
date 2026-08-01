package com.devuloopers.knet.ui.desktop.inspector.model

import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Read-only presentation model for captured HTTP response fields.
 */
public data class ResponsePresentation(
    val statusCode: Int = 200,
    val statusText: String = "OK",
    val headers: List<KeyValuePair> = emptyList(),
    val cookies: List<KeyValuePair> = emptyList(),
    val body: String = "",
    val trailers: List<KeyValuePair> = emptyList(),
    val mimeType: String = "application/json"
)
