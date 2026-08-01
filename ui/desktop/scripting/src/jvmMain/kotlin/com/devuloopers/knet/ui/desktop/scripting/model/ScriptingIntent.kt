package com.devuloopers.knet.ui.desktop.scripting.model

/**
 * Sealed interface representing user actions in `:ui:desktop:scripting`.
 */
public sealed interface ScriptingIntent {
    public data class LoadScript(val script: TrafficScript) : ScriptingIntent
    public data class UpdateCode(val code: String) : ScriptingIntent
    public object SaveScript : ScriptingIntent
    public object ExecuteScript : ScriptingIntent
    public data class AddSnippet(val snippet: String) : ScriptingIntent
    public object ClearConsole : ScriptingIntent
    public data class SetConsoleFilter(val level: String) : ScriptingIntent
    public object ToggleAutoScroll : ScriptingIntent
}
