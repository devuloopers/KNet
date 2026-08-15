package com.devuloopers.knet.ui.desktop.settings.model

import com.devuloopers.knet.domain.workspace.model.TimeoutUnit

/**
 * User actions dispatched from SettingsScreen.
 */
sealed interface SettingsIntent {
    data class SelectTab(val tab: SettingsTab) : SettingsIntent
    data class UpdateSearchQuery(val query: String) : SettingsIntent
    data class UpdateProxyPort(val port: String) : SettingsIntent
    data class ToggleAutoClearTraffic(val enabled: Boolean) : SettingsIntent
    data class SetMaxPayloadMb(val mb: Int) : SettingsIntent
    data class SetTheme(val theme: String) : SettingsIntent
    data class SetScriptLanguage(val language: String) : SettingsIntent
    data class UpdateApiStudioTimeout(val value: String, val unit: TimeoutUnit) : SettingsIntent
    data class UpdateLiveInterceptionTimeout(val value: String, val unit: TimeoutUnit) : SettingsIntent
    data object InstallRootCa : SettingsIntent
    data object OpenDataDirectory : SettingsIntent
    data object ResetDefaults : SettingsIntent
}
