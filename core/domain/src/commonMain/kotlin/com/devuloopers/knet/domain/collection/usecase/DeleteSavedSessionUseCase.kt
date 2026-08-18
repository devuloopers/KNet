package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case to delete a saved API session request from persistent storage.
 *
 * @param repository The repository managing collection data.
 */
class DeleteSavedSessionUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Executes deletion of a saved API request by its unique ID.
     *
     * @param requestId The ID of the saved request record to delete.
     */
    suspend fun execute(requestId: String) {
        repository.deleteRequest(requestId)
    }
}
