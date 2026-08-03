package com.devuloopers.knet.ui.core.components.selection

import androidx.compose.runtime.Immutable

/**
 * Generic type-safe selection model state holder.
 */
@Immutable
public data class SelectionModel<T>(
    val selectedItems: Set<T> = emptySet(),
    val isMultiSelectionAllowed: Boolean = true
) {
    public fun select(item: T): SelectionModel<T> {
        return if (isMultiSelectionAllowed) {
            copy(selectedItems = selectedItems + item)
        } else {
            copy(selectedItems = setOf(item))
        }
    }

    public fun deselect(item: T): SelectionModel<T> {
        return copy(selectedItems = selectedItems - item)
    }

    public fun toggle(item: T): SelectionModel<T> {
        return if (isSelected(item)) deselect(item) else select(item)
    }

    public fun clear(): SelectionModel<T> {
        return copy(selectedItems = emptySet())
    }

    public fun isSelected(item: T): Boolean = selectedItems.contains(item)
}
