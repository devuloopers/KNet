package com.devuloopers.knet.ui.desktop.traffic.model

/**
 * Immutable state model defining visible columns in TrafficTable using [TrafficColumn] enum set.
 *
 * @property visibleColumns Set of currently active/visible columns.
 */
data class ColumnVisibilityState(
    val visibleColumns: Set<TrafficColumn> = TrafficColumn.entries.filter { it.isDefaultVisible }.toSet()
) {
    /**
     * Checks if a specific column is currently visible.
     */
    fun isVisible(column: TrafficColumn): Boolean = column.isMandatory || visibleColumns.contains(column)

    /**
     * Toggles visibility of a non-mandatory column.
     */
    fun toggle(column: TrafficColumn): ColumnVisibilityState {
        if (column.isMandatory) return this
        val updated = if (visibleColumns.contains(column)) {
            visibleColumns - column
        } else {
            visibleColumns + column
        }
        return copy(visibleColumns = updated)
    }
}
