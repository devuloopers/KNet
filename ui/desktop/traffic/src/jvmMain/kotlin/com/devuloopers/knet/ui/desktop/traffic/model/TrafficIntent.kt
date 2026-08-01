package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Sealed interface representing user actions in `:ui:desktop:traffic`.
 */
public sealed interface TrafficIntent {
    public data class SelectTransaction(val id: String) : TrafficIntent
    public data class MultiSelect(val ids: Set<String>) : TrafficIntent
    public data class FilterByMethod(val method: String) : TrafficIntent
    public data class FilterByStatus(val statusGroup: String) : TrafficIntent
    public data class FilterByProtocol(val protocol: String) : TrafficIntent
    public data class Search(val query: String) : TrafficIntent
    public object PauseFeed : TrafficIntent
    public object ResumeFeed : TrafficIntent
    public object ClearFeed : TrafficIntent
    public object ToggleAutoScroll : TrafficIntent
}
