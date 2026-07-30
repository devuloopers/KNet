package com.devuloopers.knet.ui.apistudio.scriptanalyzer.rule

import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptAnalysisResult
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptExecutionPhase

/**
 * Interface defining a modular static analysis rule executed by [ScriptAnalyzer].
 */
interface ScriptRule {
    /**
     * Unique identifier of the analysis rule.
     */
    val ruleId: String

    /**
     * Evaluates source code within the given execution phase and returns diagnostics and quick fixes.
     *
     * @param code Script source code string.
     * @param phase Execution phase enum ([ScriptExecutionPhase]).
     * @return Analysis result produced by this rule.
     */
    fun analyze(code: String, phase: ScriptExecutionPhase): ScriptAnalysisResult
}
