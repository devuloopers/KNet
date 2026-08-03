package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Sealed interface representing all user intents / interactions in `:ui:desktop:traffic`.
 */
public sealed interface TrafficIntent {
    public data object StartCapture : TrafficIntent
    public data object StopCapture : TrafficIntent
    public data object ClearFeed : TrafficIntent
    public data object ToggleAutoScroll : TrafficIntent

    public data class Search(val query: String) : TrafficIntent
    public data class FilterByProtocol(val protocol: String) : TrafficIntent
    public data class FilterByMethod(val method: String) : TrafficIntent
    public data class FilterByStatus(val status: String) : TrafficIntent

    public data class SelectTransaction(val id: String?) : TrafficIntent
    public data class SelectInspectorTab(val tab: InspectorTab) : TrafficIntent
    public data class SelectRequestSubTab(val subTab: RequestSubTab) : TrafficIntent
    public data class SelectResponseSubTab(val subTab: ResponseSubTab) : TrafficIntent
    public data class SetPreviewFormatMode(val mode: PreviewFormatMode) : TrafficIntent
    public data class ToggleColumn(val column: TrafficColumn) : TrafficIntent
}
