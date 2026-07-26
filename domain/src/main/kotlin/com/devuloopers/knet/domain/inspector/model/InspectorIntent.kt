package com.devuloopers.knet.domain.inspector.model

/**
 * Sealed interface representing user action intents for the Inspector feature.
 */
sealed interface InspectorIntent {
    /**
     * User selected a transaction ID.
     */
    data class SelectTransaction(val transactionId: String?) : InspectorIntent

    /**
     * User clicked an inspector tab chip.
     */
    data class SelectTab(val tab: InspectorTab) : InspectorIntent
}
