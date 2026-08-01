package com.devuloopers.knet.ui.desktop.inspector.model

/**
 * Sealed interface representing user actions in `:ui:desktop:inspector`.
 */
public sealed interface InspectorIntent {
    public data class SelectTab(val tab: InspectorTab) : InspectorIntent
    public data class Search(val query: String) : InspectorIntent
    public data class SelectBodyMode(val mode: String) : InspectorIntent
    public data class SelectTransaction(val overview: TransactionOverview) : InspectorIntent
}
