package com.devuloopers.knet.ui.desktop.settings.model

import com.devuloopers.knet.scripting.model.ScriptLanguage

/**
 * Immutable UI state model for SettingsScreen.
 *
 * @property activeTab Currently selected settings section.
 * @property proxyPort Configured listening port for proxy server (default: 8080).
 * @property isCaTrusted State of Root CA certificate in OS trust store.
 * @property dataDirectory Absolute path of KNet data folder on disk (~/.knet).
 * @property autoClearTrafficOnStartup Toggle to flush traffic feed on startup.
 * @property payloadCacheLimitMb Displayed future payload cache limit.
 * @property scriptLanguage Preferred scripting language for new API Studio documents.
 * @property apiStudioTimeoutValue Numeric input string for API Studio request timeout.
 * @property apiStudioTimeoutUnit Time unit (seconds or minutes) for API Studio timeout.
 * @property liveInterceptionTimeoutValue Numeric input string for Live Interception timeout.
 * @property liveInterceptionTimeoutUnit Time unit (seconds or minutes) for Live Interception timeout.
 * @property dirtyFields Inputs changed locally but not yet committed.
 * @property savingFields Settings currently being persisted.
 * @property isInstallingCa Loading indicator for trust store installation.
 * @property notice Typed feedback notification shown to the user.
 */
data class SettingsState(
    val activeTab: SettingsTab = SettingsTab.NETWORK_PROXY,
    val isLoading: Boolean = true,
    val proxyPort: String = "8080",
    val proxyPortError: String? = null,
    val isCaTrusted: Boolean = false,
    val dataDirectory: String = "",
    val autoClearTrafficOnStartup: Boolean = false,
    val payloadCacheLimitMb: Int = 10,
    val scriptLanguage: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val apiStudioTimeoutValue: String = "60",
    val apiStudioTimeoutUnit: TimeoutUnit = TimeoutUnit.SECONDS,
    val apiStudioTimeoutError: String? = null,
    val liveInterceptionTimeoutValue: String = "60",
    val liveInterceptionTimeoutUnit: TimeoutUnit = TimeoutUnit.SECONDS,
    val liveInterceptionTimeoutError: String? = null,
    val dirtyFields: Set<SettingsField> = emptySet(),
    val savingFields: Set<SettingsField> = emptySet(),
    val isInstallingCa: Boolean = false,
    val isResetConfirmationVisible: Boolean = false,
    val notice: SettingsNotice? = null,
)

/** Independently persisted settings fields used for validation and progress reporting. */
enum class SettingsField {
    PROXY_PORT,
    AUTO_CLEAR_TRAFFIC,
    SCRIPT_LANGUAGE,
    API_STUDIO_TIMEOUT,
    LIVE_INTERCEPTION_TIMEOUT,
    RESET_DEFAULTS,
}

/** Visual meaning of a settings notification. */
enum class SettingsNoticeTone {
    SUCCESS,
    INFO,
    WARNING,
    ERROR,
}

/**
 * User-facing result from a settings operation.
 *
 * @property summary Concise footer message.
 * @property tone Semantic visual meaning.
 * @property details Optional complete instructions shown in a dialog.
 */
data class SettingsNotice(
    val summary: String,
    val tone: SettingsNoticeTone,
    val details: String? = null,
)
