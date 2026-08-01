package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Traffic table sort field and direction.
 */
public enum class TrafficSortField {
    TIME,
    METHOD,
    URL,
    STATUS,
    SIZE,
    DURATION
}

public data class TrafficSort(
    val field: TrafficSortField = TrafficSortField.TIME,
    val ascending: Boolean = false
)
