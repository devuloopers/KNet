package com.devuloopers.knet.domain.rules.model

/**
 * Sealed interface representing renderable state variations for rules.
 */
sealed interface RulesUiState {
    /** Rules list is loading off-thread. */
    data object Loading : RulesUiState

    /**
     * Successfully loaded rules configuration list.
     *
     * @property rules Active rules list.
     */
    data class Success(
        val rules: List<BreakpointRule>
    ) : RulesUiState
}
