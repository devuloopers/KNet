package com.devuloopers.knet.domain.workspace.usecase

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository

/** Applies an atomic transformation to persisted workspace layout state. */
class UpdateWorkspaceLayoutUseCase(
    private val repository: WidgetPreferencesRepository,
) {
    /**
     * Applies [transform] to the latest workspace layout.
     *
     * @param transform Pure transformation producing the updated layout.
     */
    suspend fun execute(transform: (WorkspaceLayoutSettings) -> WorkspaceLayoutSettings) {
        repository.updateSettings(transform)
    }
}
