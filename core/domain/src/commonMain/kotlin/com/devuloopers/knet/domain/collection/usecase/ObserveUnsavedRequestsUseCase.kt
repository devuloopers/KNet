package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing reactive updates to all active unsaved request session tabs in persistent storage.
 *
 * @param repository Interface for accessing collection data storage.
 */
public class ObserveUnsavedRequestsUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Executes the observation stream of active unsaved request session tabs.
     *
     * @return Flow emitting reactive list of active unsaved request session tabs.
     */
    public fun execute(): Flow<List<SavedApiRequest>> {
        return repository.observeUnsavedRequests()
    }
}
