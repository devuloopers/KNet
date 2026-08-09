package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing reactive updates to all saved API collections in persistent storage.
 *
 * @param repository Interface for accessing collection data storage.
 */
public class ObserveCollectionsUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Executes the observation stream of saved API collections.
     *
     * @return Flow emitting reactive list of saved API collections.
     */
    public fun execute(): Flow<List<ApiCollection>> {
        return repository.observeCollections()
    }
}
