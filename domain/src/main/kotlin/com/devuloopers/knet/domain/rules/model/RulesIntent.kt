package com.devuloopers.knet.domain.rules.model

/**
 * Sealed interface representing user action intents for the Rules console.
 */
sealed interface RulesIntent {
    /**
     * Toggles a rule enabled / disabled.
     */
    data class ToggleRule(val ruleId: String, val enabled: Boolean) : RulesIntent

    /**
     * Saves a new or edited rule.
     */
    data class SaveRule(val rule: RuleModel) : RulesIntent

    /**
     * Deletes a rule by ID.
     */
    data class DeleteRule(val ruleId: String) : RulesIntent

    /**
     * Switches the active console tab.
     */
    data class SelectTab(val tabName: String) : RulesIntent
}
