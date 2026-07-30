package com.devuloopers.knet.ui.apistudio.scriptanalyzer

import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptAnalysisResult
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptDiagnostic
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptExecutionPhase
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptHighlight
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.rule.PreRequestRule
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.rule.ScriptRule

/**
 * Fast, non-blocking static script analyzer orchestrator powering real-time editor diagnostics, code highlighting, and Quick Fix refactorings.
 */
class ScriptAnalyzer(
    private val rules: List<ScriptRule> = listOf(PreRequestRule())
) {

    /**
     * Runs registered analysis rules on the script source code for the specified execution phase.
     *
     * @param code Script source code string.
     * @param phase Target execution phase ([ScriptExecutionPhase]).
     * @return Aggregate static analysis result containing diagnostics, highlights, and quick fixes.
     */
    fun analyze(code: String, phase: ScriptExecutionPhase): ScriptAnalysisResult {
        if (code.isBlank()) {
            return ScriptAnalysisResult(emptyList(), emptyList(), emptyList())
        }

        val allDiagnostics = mutableListOf<ScriptDiagnostic>()
        val allHighlights = mutableListOf<ScriptHighlight>()
        val allQuickFixes = mutableListOf<ScriptQuickFix>()

        for (rule in rules) {
            val result = rule.analyze(code, phase)
            allDiagnostics.addAll(result.diagnostics)
            allHighlights.addAll(result.highlights)
            allQuickFixes.addAll(result.quickFixes)
        }

        return ScriptAnalysisResult(
            diagnostics = allDiagnostics,
            highlights = allHighlights,
            quickFixes = allQuickFixes
        )
    }
}
