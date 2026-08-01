package com.devuloopers.knet.domain.workspace.usecase

import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case retrieving reactive stream of workspace layout settings.
 *
 * @property repository Repository providing settings stream.
 */
class GetWorkspaceLayoutUseCase(
    private val repository: WidgetPreferencesRepository
) {
    /**
     * Executes the use case returning workspace layout settings flow.
     */
    fun execute(): Flow<WorkspaceLayoutSettings> = repository.settingsFlow
}
