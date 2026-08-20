package com.devuloopers.knet.domain.collection.repository

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing persistent API Collections, Folders, and Saved Requests.
 */
interface CollectionsRepository {

    /**
     * Emits the reactive list of all saved API collections with their nested folders and requests.
     */
    fun observeCollections(): Flow<List<ApiCollection>>

    /**
     * Fetches a specific collection by its unique ID.
     */
    suspend fun getCollectionById(id: String): ApiCollection?

    /**
     * Fetches a request directly by its stable identifier, regardless of whether it is saved or a draft.
     */
    suspend fun getRequestById(id: String): SavedApiRequest?

    /**
     * Saves or updates an API collection entity.
     */
    suspend fun saveCollection(collection: ApiCollection)

    /**
     * Deletes an API collection by ID.
     */
    suspend fun deleteCollection(collectionId: String)

    /**
     * Saves or updates a collection folder.
     */
    suspend fun saveFolder(collectionId: String, folder: CollectionFolder)

    /**
     * Deletes a collection folder by ID.
     */
    suspend fun deleteFolder(folderId: String)

    /**
     * Saves or updates a saved API request.
     */
    suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest)

    /**
     * Deletes a saved API request by ID.
     */
    suspend fun deleteRequest(requestId: String)

    /**
     * Emits the reactive list of all active unsaved request session tabs.
     */
    fun observeUnsavedRequests(): Flow<List<SavedApiRequest>>

    /**
     * Saves or updates an active unsaved request session tab.
     */
    suspend fun saveUnsavedRequest(request: SavedApiRequest)

    /**
     * Deletes an unsaved request session tab by ID.
     */
    suspend fun deleteUnsavedRequest(requestId: String)

    /**
     * Atomically promotes an unsaved session request to a newly created collection via a database transaction.
     */
    suspend fun saveUnsavedToNewCollectionTx(
        collection: ApiCollection,
        folder: CollectionFolder,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    )

    /**
     * Atomically inserts a request into an existing collection and removes its prior draft record.
     */
    suspend fun saveUnsavedToExistingCollectionTx(
        collectionId: String,
        folderId: String,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    )
}
