package com.devuloopers.knet.ui.core.components.selection

import androidx.compose.runtime.Immutable

/**
 * Generic type-safe selection model state holder.
 */
@Immutable
data class SelectionModel<T>(
    val selectedItems: Set<T> = emptySet(),
    val isMultiSelectionAllowed: Boolean = true
) {
    fun select(item: T): SelectionModel<T> {
        return if (isMultiSelectionAllowed) {
            copy(selectedItems = selectedItems + item)
        } else {
            copy(selectedItems = setOf(item))
        }
    }

    fun deselect(item: T): SelectionModel<T> {
        return copy(selectedItems = selectedItems - item)
    }

    fun toggle(item: T): SelectionModel<T> {
        return if (isSelected(item)) deselect(item) else select(item)
    }

    fun clear(): SelectionModel<T> {
        return copy(selectedItems = emptySet())
    }

    fun isSelected(item: T): Boolean = selectedItems.contains(item)
}
