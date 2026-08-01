package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Live traffic statistics & metric counters.
 */
public data class TrafficMetrics(
    val totalRequests: Long = 0,
    val activeSessions: Int = 1,
    val requestsPerSecond: Double = 0.0,
    val errorCount: Long = 0,
    val averageLatencyMs: Long = 0
)
