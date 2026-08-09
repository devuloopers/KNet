package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case to create a new API collection suite.
 *
 * @param repository The repository managing collection data.
 */
public class CreateCollectionUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Executes creation of a new collection with the given name.
     *
     * @param collectionName The name of the collection to create.
     */
    public suspend fun execute(collectionName: String) {
        val newCollection = ApiCollection(
            id = "col_${System.currentTimeMillis()}",
            name = collectionName.ifBlank { "New Collection" }
        )
        repository.saveCollection(newCollection)
    }
}
