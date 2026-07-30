package com.devuloopers.knet.ui.apistudio.scriptanalyzer.model

/**
 * Result data class produced by [ScriptAnalyzer] holding editor diagnostics, text highlights, and quick fixes.
 *
 * @property diagnostics List of static analysis diagnostics.
 * @property highlights List of text range highlights for editor renderers.
 * @property quickFixes List of available 1-click refactoring actions.
 */
data class ScriptAnalysisResult(
    val diagnostics: List<ScriptDiagnostic>,
    val highlights: List<ScriptHighlight>,
    val quickFixes: List<ScriptQuickFix>
)
