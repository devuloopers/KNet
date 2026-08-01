package com.devuloopers.knet.ui.desktop.scripting.model

/**
 * Script runtime log entries.
 */
public data class ConsoleLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: ConsoleLogLevel = ConsoleLogLevel.INFO,
    val message: String
)
