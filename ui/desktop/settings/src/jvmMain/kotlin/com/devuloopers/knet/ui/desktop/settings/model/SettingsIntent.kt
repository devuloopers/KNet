package com.devuloopers.knet.ui.desktop.settings.model

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
    data object InstallRootCa : SettingsIntent
    data object OpenDataDirectory : SettingsIntent
    data object ResetDefaults : SettingsIntent
}
