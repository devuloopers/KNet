package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case to delete an unsaved session from the persistent storage.
 *
 * @param repository The repository managing collection data.
 */
public class DeleteUnsavedRequestUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Executes the deletion of the specified unsaved request.
     *
     * @param requestId The ID of the unsaved request to delete.
     */
    public suspend fun execute(requestId: String) {
        repository.deleteUnsavedRequest(requestId)
    }
}
