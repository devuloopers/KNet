package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Traffic row selection state model.
 */
public data class TrafficSelection(
    val selectedIds: Set<String> = emptySet(),
    val primarySelectedId: String? = null
)
