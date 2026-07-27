package com.devuloopers.knet.domain.apistudio.repository

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
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
}
