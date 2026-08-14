package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Immutable inspector state holding asynchronously loaded body payload strings for request and response tabs.
 */
data class InspectorPreparedState(
    val transactionId: String = "",
    val requestBodyText: String = "",
    val responseBodyText: String = "",
    val isPreparing: Boolean = false
)
