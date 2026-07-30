package com.devuloopers.knet.ui.apistudio.scriptanalyzer.model

/**
 * Model representing a 1-click editor quick fix action.
 *
 * @property id Unique identifier for the quick fix.
 * @property title User-facing action title displayed on buttons and popups.
 * @property description Detailed description of the refactoring performed.
 */
sealed interface ScriptQuickFix {
    val id: String
    val title: String
    val description: String

    /**
     * Quick Fix that moves response assertion code from Pre-request Script tab to Tests tab.
     */
    data class MoveToTestsTab(
        val codeToMove: String,
        override val id: String = "quick_fix_move_to_tests",
        override val title: String = "Move to Tests Tab",
        override val description: String = "Moves pm.test and pm.response assertion code to the Tests tab where responses are available."
    ) : ScriptQuickFix
}
