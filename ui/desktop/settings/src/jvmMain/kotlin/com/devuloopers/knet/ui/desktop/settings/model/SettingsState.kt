package com.devuloopers.knet.ui.desktop.settings.model

import com.devuloopers.knet.domain.workspace.model.TimeoutUnit

/**
 * Immutable UI state model for SettingsScreen.
 *
 * @property activeTab Currently selected settings section.
 * @property proxyPort Configured listening port for proxy server (default: 8080).
 * @property isCaTrusted State of Root CA certificate in OS trust store.
 * @property dataDirectory Absolute path of KNet data folder on disk (~/.knet).
 * @property autoClearTrafficOnStartup Toggle to flush traffic feed on startup.
 * @property maxPayloadMb Max response payload size cached per transaction (default: 10 MB).
 * @property theme App UI color theme ("DARK", "LIGHT", "SYSTEM").
 * @property scriptLanguage Preferred scripting language ("JAVASCRIPT", "KOTLIN").
 * @property apiStudioTimeoutValue Numeric input string for API Studio request timeout.
 * @property apiStudioTimeoutUnit Time unit (seconds or minutes) for API Studio timeout.
 * @property liveInterceptionTimeoutValue Numeric input string for Live Interception timeout.
 * @property liveInterceptionTimeoutUnit Time unit (seconds or minutes) for Live Interception timeout.
 * @property searchQuery Text filter entered in header search bar.
 * @property isInstallingCa Loading indicator for trust store installation.
 * @property message Feedback notification message shown to user.
 */
data class SettingsState(
    val activeTab: SettingsTab = SettingsTab.NETWORK_PROXY,
    val proxyPort: String = "8080",
    val isCaTrusted: Boolean = false,
    val dataDirectory: String = "",
    val autoClearTrafficOnStartup: Boolean = false,
    val maxPayloadMb: Int = 10,
    val theme: String = "DARK",
    val scriptLanguage: String = "JAVASCRIPT",
    val apiStudioTimeoutValue: String = "60",
    val apiStudioTimeoutUnit: TimeoutUnit = TimeoutUnit.SECONDS,
    val liveInterceptionTimeoutValue: String = "60",
    val liveInterceptionTimeoutUnit: TimeoutUnit = TimeoutUnit.SECONDS,
    val searchQuery: String = "",
    val isInstallingCa: Boolean = false,
    val message: String? = null
)
