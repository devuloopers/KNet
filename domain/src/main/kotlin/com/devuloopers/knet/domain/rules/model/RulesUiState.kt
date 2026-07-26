package com.devuloopers.knet.domain.rules.model

/**
 * Sealed interface representing renderable UI state variations for the Rules console.
 */
sealed interface RulesUiState {
    /** Rules list is loading off-thread. */
    data object Loading : RulesUiState

    /**
     * Successfully loaded rules configuration list.
     *
     * @property rules Active rules list.
     * @property activeTab Active bottom tray tab (e.g. "Rules", "Breakpoints").
     */
    data class Success(
        val rules: List<RuleModel>,
        val activeTab: String = "Rules"
    ) : RulesUiState
}
