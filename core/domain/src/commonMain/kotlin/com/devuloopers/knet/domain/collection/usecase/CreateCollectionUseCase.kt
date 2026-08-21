package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Creates an empty API collection that can receive saved requests later. */
class CreateCollectionUseCase(
    private val repository: CollectionsRepository
) {
    /** Creates and persists a collection using a stable generated identifier. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(collectionName: String) {
        val newCollection = ApiCollection(
            id = "col_${Uuid.random()}",
            name = collectionName.ifBlank { "New Collection" }
        )
        repository.saveCollection(newCollection)
    }
}
