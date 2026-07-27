package com.devuloopers.knet.data.repository

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.repository.CollectionsRepository
import com.devuloopers.knet.storage.apistudio.CollectionDao
import com.devuloopers.knet.storage.apistudio.CollectionEntity
import com.devuloopers.knet.storage.apistudio.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.SavedRequestEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete data implementation of [CollectionsRepository] bridging SQLite Room entities ↔ Domain models.
 *
 * @param collectionDao The Room Data Access Object for collections data.
 */
class CollectionsRepositoryImpl(
    private val collectionDao: CollectionDao
) : CollectionsRepository {

    override fun observeCollections(): Flow<List<ApiCollection>> {
        return collectionDao.getAllCollectionsFlow().map { collectionEntities ->
            collectionEntities.map { entity ->
                mapCollectionEntityToDomain(entity)
            }
        }
    }

    override suspend fun getCollectionById(id: String): ApiCollection? {
        val entity = collectionDao.getCollectionById(id) ?: return null
        return mapCollectionEntityToDomain(entity)
    }

    override suspend fun saveCollection(collection: ApiCollection) {
        val entity = CollectionEntity(
            id = collection.id,
            name = collection.name,
            updatedAt = System.currentTimeMillis()
        )
        collectionDao.insertCollection(entity)

        // Save nested folders and requests
        collection.folders.forEachIndexed { index, folder ->
            saveFolder(collection.id, folder.copy())
        }
    }

    override suspend fun deleteCollection(collectionId: String) {
        collectionDao.deleteCollection(collectionId)
    }

    override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {
        val folderEntity = CollectionFolderEntity(
            id = folder.id,
            collectionId = collectionId,
            name = folder.name,
            isExpanded = folder.isExpanded
        )
        collectionDao.insertFolder(folderEntity)

        folder.requests.forEach { req ->
            saveRequest(collectionId, folder.id, req)
        }
    }

    override suspend fun deleteFolder(folderId: String) {
        collectionDao.deleteFolder(folderId)
    }

    override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
        val requestEntity = SavedRequestEntity(
            id = request.id,
            folderId = folderId,
            collectionId = collectionId,
            name = request.name,
            method = request.method.name,
            customMethod = request.customMethod,
            url = request.url,
            bodyContent = request.body,
            expectedStatus = request.expectedStatus
        )
        collectionDao.insertRequest(requestEntity)
    }

    override suspend fun deleteRequest(requestId: String) {
        collectionDao.deleteRequest(requestId)
    }

    private suspend fun mapCollectionEntityToDomain(entity: CollectionEntity): ApiCollection {
        val folderEntities = collectionDao.getFoldersForCollection(entity.id)
        val folders = folderEntities.map { folderEntity ->
            val requestEntities = collectionDao.getRequestsForFolder(folderEntity.id)
            val requests = requestEntities.map { reqEntity ->
                val methodEnum = try {
                    HttpMethod.valueOf(reqEntity.method)
                } catch (_: Exception) {
                    HttpMethod.CUSTOM
                }
                SavedApiRequest(
                    id = reqEntity.id,
                    name = reqEntity.name,
                    method = methodEnum,
                    customMethod = reqEntity.customMethod,
                    url = reqEntity.url,
                    body = reqEntity.bodyContent,
                    expectedStatus = reqEntity.expectedStatus
                )
            }
            CollectionFolder(
                id = folderEntity.id,
                name = folderEntity.name,
                isExpanded = folderEntity.isExpanded,
                requests = requests
            )
        }
        return ApiCollection(
            id = entity.id,
            name = entity.name,
            folders = folders
        )
    }
}
