package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Loads one API Studio request by its stable identifier without waiting for sidebar aggregation.
 *
 * @param repository Collection persistence boundary used for the direct lookup.
 */
class GetSavedRequestUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Returns the matching saved request or draft, or `null` when the identifier no longer exists.
     *
     * @param requestId Stable saved request or draft identifier.
     */
    suspend fun execute(requestId: String): SavedApiRequest? = repository.getRequestById(requestId)
}
