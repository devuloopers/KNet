package com.devuloopers.knet.ui.apistudio.view.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.devuloopers.knet.domain.apistudio.model.ApiCollection

/**
 * State holder managing the execution configuration setup phase of the [SuiteRunnerDialog].
 * Centralizes collection selection, search filtering, and request count calculations while
 * ensuring search queries never accidentally alter or clear user selections.
 * Automatically filters out empty collections containing 0 requests.
 *
 * @param collections Workspace list of [ApiCollection] items.
 * @param initialSelectedIds Set of collection identifiers to pre-select upon opening.
 */
class SuiteRunnerSetupState(
    collections: List<ApiCollection>,
    initialSelectedIds: Set<String> = emptySet()
) {
    /**
     * Workspace collections containing at least 1 saved request across folders.
     */
    val collections: List<ApiCollection> = collections.filter { collection ->
        collection.folders.sumOf { folder -> folder.requests.size } > 0
    }

    /**
     * Currently checked collection identifiers.
     */
    var selectedIds by mutableStateOf(initialSelectedIds.filter { id -> this.collections.any { col -> col.id == id } }.toSet())

    /**
     * Live search filter query text.
     */
    var searchQuery by mutableStateOf("")

    /**
     * List of executable collections matching the current [searchQuery].
     */
    val filteredCollections: List<ApiCollection>
        get() = if (searchQuery.isBlank()) {
            this.collections
        } else {
            this.collections.filter { collection ->
                collection.name.contains(searchQuery, ignoreCase = true)
            }
        }

    /**
     * Total number of API requests contained within all currently selected collections.
     */
    val totalSelectedRequests: Int
        get() = collections
            .filter { collection -> selectedIds.contains(collection.id) }
            .sumOf { collection ->
                collection.folders.sumOf { folder -> folder.requests.size }
            }

    /**
     * Toggles selection for a specific collection identifier.
     */
    fun toggleSelection(collectionId: String) {
        selectedIds = if (selectedIds.contains(collectionId)) {
            selectedIds - collectionId
        } else {
            selectedIds + collectionId
        }
    }

    /**
     * Selects all executable collections.
     */
    fun selectAll() {
        selectedIds = collections.map { collection -> collection.id }.toSet()
    }

    /**
     * Clears all collection selections.
     */
    fun clearAll() {
        selectedIds = emptySet()
    }
}
