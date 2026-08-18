package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Use case to create a new API collection suite.
 *
 * @param repository The repository managing collection data.
 */
class CreateCollectionUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Executes creation of a new collection with the given name.
     *
     * @param collectionName The name of the collection to create.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(collectionName: String) {
        val newCollection = ApiCollection(
            id = "col_${Uuid.random()}",
            name = collectionName.ifBlank { "New Collection" }
        )
        repository.saveCollection(newCollection)
    }
}
