package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case to rename an existing API collection by ID in persistent storage.
 *
 * @param repository The repository managing collection data.
 */
public class RenameCollectionUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Updates the collection name for [collectionId].
     *
     * @param collectionId ID of the collection to rename.
     * @param newName New display name for the collection.
     */
    public suspend fun execute(collectionId: String, newName: String) {
        val collection = repository.getCollectionById(collectionId)
        if (collection != null) {
            repository.saveCollection(collection.copy(name = newName))
        }
    }
}
