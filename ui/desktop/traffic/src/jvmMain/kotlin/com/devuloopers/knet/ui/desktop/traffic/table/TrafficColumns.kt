package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Traffic table column widths and headers definition.
 */
public enum class TrafficColumn(public val label: String, public val width: Dp) {
    STATUS("Status", 70.dp),
    METHOD("Method", 70.dp),
    URL("URL / Path", 280.dp),
    HOST("Host", 150.dp),
    TYPE("Type", 90.dp),
    TIME("Time", 90.dp),
    SIZE("Size", 80.dp)
}
