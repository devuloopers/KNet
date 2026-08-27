package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.traffic.id.ProtocolMessageId


/**
 * Sealed interface representing all user intents / interactions in `:ui:desktop:traffic`.
 */
sealed interface TrafficIntent {
    data object StartCapture : TrafficIntent
    data object PauseCapture : TrafficIntent
    data object ClearFeed : TrafficIntent
    data object ToggleAutoScroll : TrafficIntent
    data object DismissEngineError : TrafficIntent
    data object LoadNextPage : TrafficIntent

    data class Search(val query: String) : TrafficIntent
    data class FilterByScheme(val scheme: SchemeFilter) : TrafficIntent
    data class FilterByHttpVersion(val version: HttpVersionFilter) : TrafficIntent
    data class FilterByMethod(val method: MethodFilter) : TrafficIntent
    data class FilterByStatus(val status: StatusFilter) : TrafficIntent

    data class SelectTransaction(val id: String?) : TrafficIntent
    data class SelectInspectorTab(val tab: InspectorTab) : TrafficIntent
    data class SelectProtocolMessage(val id: ProtocolMessageId) : TrafficIntent
    data object LoadNextProtocolMessagePage : TrafficIntent
    data class SelectRequestSubTab(val subTab: InspectorSubTab) : TrafficIntent
    data class SelectResponseSubTab(val subTab: InspectorSubTab) : TrafficIntent
    data class ToggleColumn(val column: TrafficColumn) : TrafficIntent

    /** Applies one constrained, presentation-only column width during a drag gesture. */
    data class ResizeColumn(val column: TrafficColumn, val widthDp: Float) : TrafficIntent

    /** Persists the current column-width snapshot after a resize gesture finishes. */
    data object CommitColumnWidths : TrafficIntent

    /** Restores and persists one column's default sizing mode. */
    data class ResetColumnWidth(val column: TrafficColumn) : TrafficIntent

    /** Restores and persists all Traffic column defaults. */
    data object ResetColumnWidths : TrafficIntent
}
