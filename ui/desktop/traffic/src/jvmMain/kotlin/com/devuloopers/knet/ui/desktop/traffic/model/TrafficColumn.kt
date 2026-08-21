package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Enum representing toggleable and mandatory table columns in the TrafficTable workspace.
 *
 * @property displayName Human-readable label rendered in table headers and dropdowns.
 * @property headerLabel Compact label rendered in the table header.
 * @property isMandatory If true, column cannot be hidden by user toggle.
 * @property isDefaultVisible If true, column starts visible on initial launch.
 */
enum class TrafficColumn(
    val displayName: String,
    val headerLabel: String,
    val isMandatory: Boolean = false,
    val isDefaultVisible: Boolean = true
) {
    SERIAL_NUMBER("Serial Number (#)", "#", isMandatory = false, isDefaultVisible = true),
    TIMESTAMP("Timestamp", "Timestamp", isMandatory = false, isDefaultVisible = true),
    METHOD("Method", "Method", isMandatory = true, isDefaultVisible = true),
    PROTOCOL("Protocol", "Protocol", isMandatory = false, isDefaultVisible = false),
    STREAM("Stream ID", "Stream", isMandatory = false, isDefaultVisible = false),
    SOURCE("Source", "Source", isMandatory = false, isDefaultVisible = false),
    HOST("Host", "Host", isMandatory = true, isDefaultVisible = true),
    PATH("Path", "Path", isMandatory = true, isDefaultVisible = true),
    STATUS("Status", "Status", isMandatory = false, isDefaultVisible = true),
    SIZE("Size", "Size", isMandatory = false, isDefaultVisible = true),
    DURATION("Duration", "Duration", isMandatory = false, isDefaultVisible = true),
    TYPE("Type", "Type", isMandatory = false, isDefaultVisible = false)
}
