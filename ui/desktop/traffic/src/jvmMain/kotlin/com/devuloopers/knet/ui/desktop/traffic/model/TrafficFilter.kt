package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Traffic filter criteria model.
 */
public data class TrafficFilter(
    val method: String = "ALL",
    val statusGroup: String = "ALL",
    val protocol: String = "ALL",
    val domain: String = "",
    val searchQuery: String = ""
)
