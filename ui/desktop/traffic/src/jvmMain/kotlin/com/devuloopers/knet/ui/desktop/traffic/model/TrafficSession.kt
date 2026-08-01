package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Traffic capture session grouping model.
 */
public data class TrafficSession(
    val sessionId: String = "session_default",
    val name: String = "Active Capture",
    val isRecording: Boolean = true,
    val startedAtMs: Long = System.currentTimeMillis()
)
