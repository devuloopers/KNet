package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.ui.desktop.codeeditor.model.PreparedDocument

/**
 * Immutable inspector state holding asynchronously precomputed documents for request and response tabs.
 *
 * Adheres to KNet UI Specification: Inspector Background Preparation Pipeline v1.0.
 */
data class InspectorPreparedState(
    val transactionId: String = "",
    val requestBody: PreparedDocument = PreparedDocument(),
    val responseBody: PreparedDocument = PreparedDocument(),
    val isPreparing: Boolean = false
)
