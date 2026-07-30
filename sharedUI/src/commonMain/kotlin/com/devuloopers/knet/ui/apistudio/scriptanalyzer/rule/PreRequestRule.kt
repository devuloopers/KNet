package com.devuloopers.knet.ui.apistudio.scriptanalyzer.rule

import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.DiagnosticSeverity
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptAnalysisResult
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptDiagnostic
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptExecutionPhase
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptHighlight
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.TextRange

/**
 * Static analysis rule detecting invalid use of `pm.response` and `pm.test` assertions in Pre-request Scripts.
 */
class PreRequestRule : ScriptRule {

    override val ruleId: String = "pre_request_assertion_rule"

    override fun analyze(code: String, phase: ScriptExecutionPhase): ScriptAnalysisResult {
        if (phase != ScriptExecutionPhase.PRE_REQUEST || code.isBlank()) {
            return ScriptAnalysisResult(emptyList(), emptyList(), emptyList())
        }

        val diagnostics = mutableListOf<ScriptDiagnostic>()
        val highlights = mutableListOf<ScriptHighlight>()
        val quickFixes = mutableListOf<ScriptQuickFix>()

        val hasResponseAccess = code.contains("pm.response")
        val hasTestAssertions = code.contains("pm.test")

        if (hasResponseAccess || hasTestAssertions) {
            val quickFix = ScriptQuickFix.MoveToTestsTab(codeToMove = code)
            quickFixes.add(quickFix)

            val diagMsg = "pm.response and pm.test assertions run after the HTTP request is completed. Move this code to the Tests tab."
            diagnostics.add(
                ScriptDiagnostic.PreRequestAssertionWarning(
                    message = diagMsg,
                    quickFixes = listOf(quickFix)
                )
            )

            // Find character offsets for text highlighting
            val targetIndex = code.indexOf("pm.response").takeIf { it >= 0 }
                ?: code.indexOf("pm.test").takeIf { it >= 0 } ?: 0
            val targetLength = if (code.contains("pm.response")) "pm.response".length else "pm.test".length

            highlights.add(
                ScriptHighlight(
                    range = TextRange(targetIndex, targetIndex + targetLength),
                    tooltip = "pm.response is unavailable in Pre-request scripts. Move code to Tests tab.",
                    severity = DiagnosticSeverity.WARNING
                )
            )
        }

        return ScriptAnalysisResult(diagnostics, highlights, quickFixes)
    }
}
