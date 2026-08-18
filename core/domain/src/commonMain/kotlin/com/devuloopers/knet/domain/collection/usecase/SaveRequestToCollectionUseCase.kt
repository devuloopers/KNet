package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository

/**
 * Use case for saving or promoting an unsaved request into a persistent collection (either existing or newly created).
 *
 * @param repository Interface for accessing collection data storage.
 */
class SaveRequestToCollectionUseCase(
    private val repository: CollectionsRepository
) {
    /**
     * Promotes an unsaved request into a new collection atomically via a database transaction.
     *
     * @param collection Target new collection model to create.
     * @param folder Target folder inside the collection to create.
     * @param request Saved request model to assign.
     * @param unsavedRequestIdToDelete Id of transient unsaved session to remove upon promotion.
     */
    suspend fun executeNew(
        collection: ApiCollection,
        folder: CollectionFolder,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    ) {
        repository.saveUnsavedToNewCollectionTx(
            collection = collection,
            folder = folder,
            request = request,
            unsavedRequestIdToDelete = unsavedRequestIdToDelete
        )
    }

    /**
     * Saves a request into an existing collection and folder.
     *
     * @param collectionId ID of the target existing collection.
     * @param folderId ID of the target folder inside the collection.
     * @param request Saved request model to insert or update.
     * @param unsavedRequestIdToDelete Optional unsaved request ID to delete if promoting from unsaved session.
     */
    suspend fun executeExisting(
        collectionId: String,
        folderId: String,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String? = null
    ) {
        repository.saveRequest(collectionId = collectionId, folderId = folderId, request = request)
        if (unsavedRequestIdToDelete != null) {
            repository.deleteUnsavedRequest(unsavedRequestIdToDelete)
        }
    }
}
