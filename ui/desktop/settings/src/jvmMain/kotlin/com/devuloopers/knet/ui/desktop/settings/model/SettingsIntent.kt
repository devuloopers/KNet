package com.devuloopers.knet.ui.desktop.settings.model

import com.devuloopers.knet.scripting.model.ScriptLanguage

/**
 * User actions dispatched from SettingsScreen.
 */
sealed interface SettingsIntent {
    data class SelectTab(val tab: SettingsTab) : SettingsIntent
    data class UpdateProxyPort(val port: String) : SettingsIntent
    data object CommitProxyPort : SettingsIntent
    data class ToggleAutoClearTraffic(val enabled: Boolean) : SettingsIntent
    data class SetScriptLanguage(val language: ScriptLanguage) : SettingsIntent
    data class UpdateApiStudioTimeout(val value: String, val unit: TimeoutUnit) : SettingsIntent
    data object CommitApiStudioTimeout : SettingsIntent
    data class UpdateLiveInterceptionTimeout(val value: String, val unit: TimeoutUnit) : SettingsIntent
    data object CommitLiveInterceptionTimeout : SettingsIntent
    data object InstallRootCa : SettingsIntent
    data object OpenDataDirectory : SettingsIntent
    data object RequestResetDefaults : SettingsIntent
    data object CancelResetDefaults : SettingsIntent
    data object ConfirmResetDefaults : SettingsIntent
    data object DismissNotice : SettingsIntent
}
