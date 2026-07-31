package com.devuloopers.knet.ui.apistudio.handler

import com.devuloopers.knet.domain.apistudio.exporter.PostmanCollectionExporter
import com.devuloopers.knet.domain.apistudio.importer.PostmanCollectionImporter
import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.repository.CollectionsRepository
import kotlin.uuid.Uuid

/**
 * Pure handler managing API collection tree navigation, folder creation, request saving, deletion, and Postman import/export.
 *
 * @param repository Optional persistence repository.
 */
class CollectionHandler(
    private val repository: CollectionsRepository? = null
) {
    private val importer = PostmanCollectionImporter()
    private val exporter = PostmanCollectionExporter()

    /**
     * Creates a new collection and adds it to the existing collection list.
     */
    fun createCollection(
        collections: List<ApiCollection>,
        name: String,
        description: String = ""
    ): Pair<List<ApiCollection>, ApiCollection> {
        val newCol = ApiCollection(
            id = "col_${System.currentTimeMillis()}",
            name = name
        )
        val updated = collections + newCol
        return updated to newCol
    }

    /**
     * Deletes an entire collection by ID.
     */
    fun deleteCollection(
        collections: List<ApiCollection>,
        collectionId: String,
        selectedRequestId: String?
    ): Pair<List<ApiCollection>, SavedApiRequest?> {
        val updatedCollections = collections.filter { it.id != collectionId }
        val isDeleted = collections.find { it.id == collectionId }?.folders?.flatMap { it.requests }?.any { it.id == selectedRequestId } == true
        val newSelected = if (isDeleted) {
            updatedCollections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull()
        } else null

        return updatedCollections to newSelected
    }

    /**
     * Deletes a request from a collection folder.
     */
    fun deleteRequest(
        collections: List<ApiCollection>,
        collectionId: String,
        requestId: String,
        selectedRequestId: String?
    ): Pair<List<ApiCollection>, SavedApiRequest?> {
        val updatedCollections = collections.map { col ->
            if (col.id == collectionId) {
                col.copy(folders = col.folders.map { folder ->
                    folder.copy(requests = folder.requests.filter { it.id != requestId })
                })
            } else col
        }
        val isDeleted = selectedRequestId == requestId
        val newSelected = if (isDeleted) {
            updatedCollections.flatMap { it.folders }.flatMap { it.requests }.firstOrNull()
        } else null

        return updatedCollections to newSelected
    }

    /**
     * Creates a new folder inside a collection.
     */
    fun createFolder(
        collections: List<ApiCollection>,
        collectionId: String,
        folderName: String
    ): List<ApiCollection> {
        val newFolder = CollectionFolder(
            id = "folder_${System.currentTimeMillis()}",
            name = folderName
        )
        return collections.map { col ->
            if (col.id == collectionId) col.copy(folders = col.folders + newFolder) else col
        }
    }

    /**
     * Creates a new request inside a folder.
     */
    fun createRequestInFolder(
        collections: List<ApiCollection>,
        collectionId: String,
        folderId: String,
        requestName: String
    ): Pair<List<ApiCollection>, SavedApiRequest> {
        val newReq = SavedApiRequest(
            id = "req_${System.currentTimeMillis()}",
            name = requestName,
            method = HttpMethod.GET,
            url = "http://127.0.0.1:9090/api/test/get"
        )
        val updated = collections.map { col ->
            if (col.id == collectionId) {
                col.copy(folders = col.folders.map { folder ->
                    if (folder.id == folderId) folder.copy(requests = folder.requests + newReq) else folder
                })
            } else col
        }
        return updated to newReq
    }

    /**
     * Imports a Postman Collection JSON payload string.
     */
    fun importPostmanCollection(
        collections: List<ApiCollection>,
        jsonContent: String
    ): Pair<List<ApiCollection>, ApiCollection> {
        val imported = importer.parseJson(jsonContent)
        val updated = collections + imported
        return updated to imported
    }

    /**
     * Exports an API collection into Postman Collection JSON format.
     */
    fun exportPostmanCollection(collection: ApiCollection): String {
        return exporter.exportToJson(collection)
    }

    /**
     * Atomically promotes an unsaved session request into a newly created collection with a default folder.
     */
    fun saveUnsavedToNewCollection(
        collections: List<ApiCollection>,
        unsavedRequests: List<SavedApiRequest>,
        selectedRequest: SavedApiRequest?,
        requestId: String,
        collectionName: String,
        requestName: String? = null
    ): Triple<List<ApiCollection>, List<SavedApiRequest>, SavedApiRequest?> {
        val unsavedReq = unsavedRequests.find { it.id == requestId } ?: selectedRequest ?: return Triple(collections, unsavedRequests, null)
        val promotedReq = unsavedReq.copy(
            id = "req-${Uuid.random()}",
            name = requestName?.takeIf { it.isNotBlank() } ?: unsavedReq.name
        )

        val defaultFolder = CollectionFolder(
            id = "folder-${Uuid.random()}",
            name = "General",
            requests = listOf(promotedReq)
        )

        val newCol = ApiCollection(
            id = "col-${Uuid.random()}",
            name = collectionName.takeIf { it.isNotBlank() } ?: "New Collection",
            folders = listOf(defaultFolder)
        )

        val updatedCollections = collections + newCol
        val updatedUnsaved = unsavedRequests.filter { it.id != requestId }

        return Triple(updatedCollections, updatedUnsaved, promotedReq)
    }
}
