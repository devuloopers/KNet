package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab

import com.devuloopers.knet.domain.traffic.model.MethodFilter
import com.devuloopers.knet.domain.traffic.model.ProtocolFilter
import com.devuloopers.knet.domain.traffic.model.StatusFilter

/**
 * Sealed interface representing all user intents / interactions in `:ui:desktop:traffic`.
 */
sealed interface TrafficIntent {
    data object StartCapture : TrafficIntent
    data object StopCapture : TrafficIntent
    data object ClearFeed : TrafficIntent
    data object ToggleAutoScroll : TrafficIntent
    data object DismissEngineError : TrafficIntent
    data object LoadNextPage : TrafficIntent

    data class Search(val query: String) : TrafficIntent
    data class FilterByProtocol(val protocol: ProtocolFilter) : TrafficIntent
    data class FilterByMethod(val method: MethodFilter) : TrafficIntent
    data class FilterByStatus(val status: StatusFilter) : TrafficIntent

    data class SelectTransaction(val id: String?) : TrafficIntent
    data class SelectInspectorTab(val tab: InspectorTab) : TrafficIntent
    data class SelectRequestSubTab(val subTab: InspectorSubTab) : TrafficIntent
    data class SelectResponseSubTab(val subTab: InspectorSubTab) : TrafficIntent
    data class SetPreviewFormatMode(val mode: PreviewFormatMode) : TrafficIntent
    data class ToggleColumn(val column: TrafficColumn) : TrafficIntent
}
