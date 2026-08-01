package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.domain.traffic.model.TrafficItemUiState

/**
 * Top-level UI state DTO for `:ui:desktop:traffic`.
 */
public data class TrafficState(
    val transactions: List<TrafficItemUiState> = emptyList(),
    val filteredTransactions: List<TrafficItemUiState> = emptyList(),
    val filter: TrafficFilter = TrafficFilter(),
    val sort: TrafficSort = TrafficSort(),
    val selection: TrafficSelection = TrafficSelection(),
    val metrics: TrafficMetrics = TrafficMetrics(),
    val isPaused: Boolean = false,
    val autoScroll: Boolean = true
)
