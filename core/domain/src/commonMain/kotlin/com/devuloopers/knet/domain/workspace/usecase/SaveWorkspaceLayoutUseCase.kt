package com.devuloopers.knet.domain.workspace.usecase

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository

/**
 * Use case persisting updated workspace layout settings.
 *
 * @property repository Repository persisting settings.
 */
class SaveWorkspaceLayoutUseCase(
    private val repository: WidgetPreferencesRepository
) {
    /**
     * Executes settings persistence off-thread.
     *
     * @param settings Updated layout settings model.
     */
    suspend fun execute(settings: WorkspaceLayoutSettings) {
        repository.saveSettings(settings)
    }
}
