package com.devuloopers.knet.ui.desktop.inspector.model

/**
 * Top-level UI state DTO for `:ui:desktop:inspector`.
 */
public data class InspectorState(
    val activeTab: InspectorTab = InspectorTab.OVERVIEW,
    val overview: TransactionOverview? = null,
    val request: RequestPresentation = RequestPresentation(),
    val response: ResponsePresentation = ResponsePresentation(),
    val searchQuery: String = "",
    val bodyMode: String = "Pretty"
)
