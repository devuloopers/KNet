package com.devuloopers.knet.domain.workspace.repository

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract managing workspace layout settings persistence.
 */
interface WidgetPreferencesRepository {

    /** Reactive stream emitting current workspace layout settings. */
    val settingsFlow: Flow<WorkspaceLayoutSettings>

    /**
     * Persists updated workspace layout settings.
     *
     * @param settings Updated layout settings.
     */
    suspend fun saveSettings(settings: WorkspaceLayoutSettings)
}
