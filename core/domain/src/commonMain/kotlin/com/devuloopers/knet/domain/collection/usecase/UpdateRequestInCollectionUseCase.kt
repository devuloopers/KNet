package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case for updating an existing saved API request **in-place** inside its collection folder.
 *
 * Used when the user edits a saved collection request directly in the API Studio editor.
 * Unlike [SaveRequestToCollectionUseCase], this performs a targeted upsert on an already-saved
 * request without touching unsaved sessions or creating any new records.
 *
 * @param repository Interface for accessing collection data storage.
 */
public class UpdateRequestInCollectionUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Updates an existing saved request inside the specified collection folder.
     *
     * @param collectionId The ID of the parent collection.
     * @param folderId The ID of the parent folder within the collection.
     * @param request The updated [SavedApiRequest] to persist. Must retain its original ID.
     */
    public suspend fun execute(
        collectionId: String,
        folderId: String,
        request: SavedApiRequest
    ) {
        repository.saveRequest(
            collectionId = collectionId,
            folderId = folderId,
            request = request
        )
    }
}
