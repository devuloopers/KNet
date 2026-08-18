package com.devuloopers.knet.ui.desktop.scripting.model

/**
 * Script syntax static analysis diagnostics.
 */
data class ScriptDiagnostic(
    val line: Int,
    val message: String,
    val severity: ScriptDiagnosticSeverity
)
