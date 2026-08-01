package com.devuloopers.knet.core.logger

import co.touchlab.kermit.Severity

/**
 * Data class representing global configuration parameters for logger instances.
 *
 * @property minimumSeverity Minimum log severity level to process (default: Severity.Info).
 * @property enableThreadName Whether log outputs include the thread name prefix.
 * @property enableTimestamp Whether log outputs include precise execution timestamps.
 */
data class LoggerConfiguration(
    val minimumSeverity: Severity = Severity.Info,
    val enableThreadName: Boolean = false,
    val enableTimestamp: Boolean = true
)
