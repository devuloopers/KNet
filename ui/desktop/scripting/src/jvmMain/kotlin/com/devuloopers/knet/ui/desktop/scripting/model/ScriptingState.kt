package com.devuloopers.knet.ui.desktop.scripting.model

import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.scripting.model.ScriptPhase

/**
 * Top-level UI state DTO for `:ui:desktop:scripting`.
 */
data class TrafficScript(
    val id: String = "",
    val name: String = "script.js",
    val code: String = "",
    val language: ScriptLanguage = ScriptLanguage.JAVASCRIPT,
    val phase: ScriptPhase = ScriptPhase.PRE_REQUEST,
    val isDirty: Boolean = false
)

data class ScriptingState(
    val activeScript: TrafficScript? = null,
    val openScripts: List<TrafficScript> = emptyList(),
    val executionState: ScriptExecutionState = ScriptExecutionState.IDLE,
    val context: ExecutionContext = ExecutionContext(),
    val diagnostics: List<ScriptDiagnostic> = emptyList(),
    val logs: List<ConsoleLogEntry> = emptyList(),
    val autoScroll: Boolean = true,
    val consoleFilter: String = "ALL"
)
