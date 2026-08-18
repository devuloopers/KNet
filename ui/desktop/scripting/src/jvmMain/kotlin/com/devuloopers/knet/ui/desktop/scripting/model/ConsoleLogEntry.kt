package com.devuloopers.knet.ui.desktop.scripting.model

import kotlin.time.Clock

/**
 * Script runtime log entries.
 */
data class ConsoleLogEntry(
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val level: ConsoleLogLevel = ConsoleLogLevel.INFO,
    val message: String
)
