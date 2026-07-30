package com.devuloopers.knet.ui.apistudio.viewmodel.handler

import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.ScriptAnalyzer
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptAnalysisResult
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptExecutionPhase
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix

/**
 * Result data class for script update and analysis operations.
 */
data class ScriptUpdateOutcome(
    val updatedRequest: SavedApiRequest,
    val analysisResult: ScriptAnalysisResult?
)

/**
 * Result data class for Quick Fix refactoring actions.
 */
data class QuickFixOutcome(
    val updatedRequest: SavedApiRequest,
    val targetTab: String = "Tests"
)

/**
 * Pure handler managing script text updates (Pre-request & Tests), real-time [ScriptAnalyzer] static analysis, and Quick Fix refactorings.
 */
class ScriptHandler {
    private val scriptAnalyzer = ScriptAnalyzer()

    /**
     * Updates Pre-request script text and runs real-time static analysis.
     */
    fun updatePreRequestScript(
        targetRequest: SavedApiRequest,
        script: String
    ): ScriptUpdateOutcome {
        val updated = targetRequest.copy(scripts = targetRequest.scripts.copy(preRequest = script))
        val analysis = scriptAnalyzer.analyze(script, ScriptExecutionPhase.PRE_REQUEST)
        return ScriptUpdateOutcome(
            updatedRequest = updated,
            analysisResult = analysis
        )
    }

    /**
     * Updates Test script text and runs real-time static analysis.
     */
    fun updateTestScript(
        targetRequest: SavedApiRequest,
        script: String
    ): ScriptUpdateOutcome {
        val updated = targetRequest.copy(scripts = targetRequest.scripts.copy(test = script))
        val analysis = scriptAnalyzer.analyze(script, ScriptExecutionPhase.POST_RESPONSE)
        return ScriptUpdateOutcome(
            updatedRequest = updated,
            analysisResult = analysis
        )
    }

    /**
     * Updates target scripting language enum (JavaScript / Kotlin).
     */
    fun updateScriptLanguage(
        targetRequest: SavedApiRequest,
        language: ScriptLanguage
    ): SavedApiRequest {
        return targetRequest.copy(scripts = targetRequest.scripts.copy(language = language))
    }

    /**
     * Applies a 1-click Quick Fix refactoring action (e.g. moving pm.test code to Tests tab).
     */
    fun applyQuickFix(
        targetRequest: SavedApiRequest,
        quickFix: ScriptQuickFix
    ): QuickFixOutcome {
        return when (quickFix) {
            is ScriptQuickFix.MoveToTestsTab -> {
                val existingTests = targetRequest.scripts.test
                val newTests = if (existingTests.isNotBlank()) "$existingTests\n\n${quickFix.codeToMove}" else quickFix.codeToMove
                val updated = targetRequest.copy(
                    scripts = targetRequest.scripts.copy(
                        preRequest = "",
                        test = newTests
                    )
                )
                QuickFixOutcome(
                    updatedRequest = updated,
                    targetTab = "Tests"
                )
            }
        }
    }
}
