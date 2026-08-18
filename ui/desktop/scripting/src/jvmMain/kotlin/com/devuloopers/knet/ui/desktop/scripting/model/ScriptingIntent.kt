package com.devuloopers.knet.ui.desktop.scripting.model

/**
 * Sealed interface representing user actions in `:ui:desktop:scripting`.
 */
sealed interface ScriptingIntent {
    data class LoadScript(val script: TrafficScript) : ScriptingIntent
    data class UpdateCode(val code: String) : ScriptingIntent
    object SaveScript : ScriptingIntent
    object ExecuteScript : ScriptingIntent
    data class AddSnippet(val snippet: String) : ScriptingIntent
    object ClearConsole : ScriptingIntent
    data class SetConsoleFilter(val level: String) : ScriptingIntent
    object ToggleAutoScroll : ScriptingIntent
}
