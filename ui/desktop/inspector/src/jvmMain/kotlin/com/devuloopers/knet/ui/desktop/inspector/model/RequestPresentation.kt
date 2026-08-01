package com.devuloopers.knet.ui.desktop.inspector.model

import com.devuloopers.knet.ui.core.table.KeyValuePair

/**
 * Read-only presentation model for captured HTTP request fields.
 */
public data class RequestPresentation(
    val headers: List<KeyValuePair> = emptyList(),
    val queryParams: List<KeyValuePair> = emptyList(),
    val cookies: List<KeyValuePair> = emptyList(),
    val body: String = "",
    val trailers: List<KeyValuePair> = emptyList()
)
