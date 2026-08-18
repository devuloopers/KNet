package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case to delete an API collection by ID from persistent storage.
 *
 * @param repository The repository managing collection data.
 */
class DeleteCollectionUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Deletes the collection matching [collectionId].
     *
     * @param collectionId ID of the collection to delete.
     */
    suspend fun execute(collectionId: String) {
        repository.deleteCollection(collectionId)
    }
}
