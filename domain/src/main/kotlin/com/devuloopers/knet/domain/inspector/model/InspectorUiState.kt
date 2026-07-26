package com.devuloopers.knet.domain.inspector.model

/**
 * Sealed interface representing all renderable state variations for the Inspector panel.
 */
sealed interface InspectorUiState {
    /** No transaction is currently selected. */
    data object NoSelection : InspectorUiState

    /** Transaction detail is currently loading off-thread. */
    data object Loading : InspectorUiState

    /**
     * Successfully loaded transaction details ready for UI rendering.
     *
     * @property transaction Detailed transaction UI model.
     * @property activeTab Currently active inspector tab.
     */
    data class Success(
        val transaction: TransactionUiModel,
        val activeTab: InspectorTab = InspectorTab.OVERVIEW
    ) : InspectorUiState
}
