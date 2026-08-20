package com.devuloopers.knet.domain.workspace.repository

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import kotlinx.coroutines.flow.Flow

/** Domain repository contract managing workspace layout persistence. */
interface WidgetPreferencesRepository {

    /** Reactive stream emitting current workspace layout settings. */
    val settingsFlow: Flow<WorkspaceLayoutSettings>

    /**
     * Atomically transforms the latest workspace layout.
     *
     * @param transform Pure transformation applied to the current persisted layout.
     */
    suspend fun updateSettings(transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings)
}
