package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Enum representing toggleable and mandatory table columns in the TrafficTable workspace.
 *
 * @property displayName Human-readable label rendered in table headers and dropdowns.
 * @property isMandatory If true, column cannot be hidden by user toggle.
 * @property isDefaultVisible If true, column starts visible on initial launch.
 */
enum class TrafficColumn(
    val displayName: String,
    val isMandatory: Boolean = false,
    val isDefaultVisible: Boolean = true
) {
    SERIAL_NUMBER("Serial Number (#)", isMandatory = false, isDefaultVisible = true),
    TIMESTAMP("Timestamp", isMandatory = false, isDefaultVisible = true),
    METHOD("Method", isMandatory = true, isDefaultVisible = true),
    HOST("Host", isMandatory = true, isDefaultVisible = true),
    PATH("Path", isMandatory = true, isDefaultVisible = true),
    STATUS("Status", isMandatory = false, isDefaultVisible = true),
    SIZE("Size", isMandatory = false, isDefaultVisible = true),
    DURATION("Duration", isMandatory = false, isDefaultVisible = true),
    TYPE("Type", isMandatory = false, isDefaultVisible = false)
}
