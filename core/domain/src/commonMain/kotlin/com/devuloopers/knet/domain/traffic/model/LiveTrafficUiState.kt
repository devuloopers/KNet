package com.devuloopers.knet.domain.traffic.model

/**
 * Immutable sealed interface representing all rendering states for the Live Traffic feed widget.
 */
sealed interface LiveTrafficUiState {

    /** Indicates the traffic feed is initializing. */
    data object Loading : LiveTrafficUiState

    /** Indicates no transactions match the active filter or traffic history is clear. */
    data class Empty(
        val activeFilter: ProtocolFilter = ProtocolFilter.ALL,
        val searchQuery: String = ""
    ) : LiveTrafficUiState

    /**
     * Successful rendering state containing filtered transactions.
     *
     * @property items List of pre-calculated UI items for list rendering.
     * @property totalCount Count of matching filtered items.
     * @property activeFilter Currently active protocol chip filter.
     * @property searchQuery Active text search query.
     * @property selectedItem Currently highlighted transaction item, or null.
     */
    data class Success(
        val items: List<TrafficItemUiState>,
        val totalCount: Int,
        val activeFilter: ProtocolFilter,
        val searchQuery: String,
        val selectedItem: TrafficItemUiState? = null
    ) : LiveTrafficUiState
}
