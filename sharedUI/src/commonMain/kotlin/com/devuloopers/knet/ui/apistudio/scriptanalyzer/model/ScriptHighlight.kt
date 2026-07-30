package com.devuloopers.knet.ui.apistudio.scriptanalyzer.model

/**
 * Editor text highlight marker representing problematic code regions.
 *
 * @property range Target character range.
 * @property tooltip Diagnostic hover tooltip.
 * @property severity Underline/highlight severity level.
 */
data class ScriptHighlight(
    val range: TextRange,
    val tooltip: String,
    val severity: DiagnosticSeverity
)
