package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case for persisting or updating an active unsaved request session tab in persistent storage.
 *
 * @param repository Interface for accessing collection data storage.
 */
class SaveUnsavedRequestUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Persists or updates the specified unsaved request entity.
     *
     * @param request Target unsaved API request entity to save.
     */
    suspend fun execute(request: SavedApiRequest) {
        repository.saveUnsavedRequest(request)
    }
}
