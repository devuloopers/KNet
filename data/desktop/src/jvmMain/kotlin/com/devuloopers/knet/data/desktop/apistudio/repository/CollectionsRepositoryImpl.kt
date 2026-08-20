package com.devuloopers.knet.data.desktop.apistudio.repository

import com.devuloopers.knet.data.desktop.mapper.CollectionMapper
import com.devuloopers.knet.data.desktop.mapper.RequestMapper
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.storage.apistudio.dao.CollectionDao
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Concrete desktop implementation of [CollectionsRepository] bridging SQLite Room entities ↔ Domain models.
 */
class CollectionsRepositoryImpl(
    private val collectionDao: CollectionDao
) : CollectionsRepository {

    companion object {
        private const val UNSAVED_COLLECTION_ID = "c-unsaved"
    }

    override fun observeCollections(): Flow<List<ApiCollection>> {
        return combine(
            collectionDao.getAllCollectionsFlow(),
            collectionDao.getAllFoldersFlow(),
            collectionDao.getAllRequestsFlow()
        ) { collectionEntities, folderEntities, requestEntities ->
            val nonUnsavedCollections = collectionEntities.filter { it.id != UNSAVED_COLLECTION_ID }
            val foldersByCollection = folderEntities.groupBy { it.collectionId }
            val requestsByFolder = requestEntities.groupBy { it.folderId }
            val requestsByCollection = requestEntities.groupBy { it.collectionId }

            nonUnsavedCollections.map { colEntity ->
                val colFolders = foldersByCollection[colEntity.id] ?: emptyList()
                val domainFolders = if (colFolders.isNotEmpty()) {
                    colFolders.map { folderEntity ->
                        val folderRequests = requestsByFolder[folderEntity.id] ?: emptyList()
                        CollectionFolder(
                            id = folderEntity.id,
                            name = folderEntity.name,
                            isExpanded = folderEntity.isExpanded,
                            requests = folderRequests.map { RequestMapper.mapEntityToDomain(it) }
                        )
                    }
                } else {
                    val directRequests = requestsByCollection[colEntity.id] ?: emptyList()
                    if (directRequests.isNotEmpty()) {
                        listOf(
                            CollectionFolder(
                                id = colEntity.id,
                                name = colEntity.name,
                                isExpanded = true,
                                requests = directRequests.map { RequestMapper.mapEntityToDomain(it) }
                            )
                        )
                    } else {
                        emptyList()
                    }
                }

                ApiCollection(
                    id = colEntity.id,
                    name = colEntity.name,
                    folders = domainFolders
                )
            }
        }
    }

    override suspend fun getCollectionById(id: String): ApiCollection? {
        val entity = collectionDao.getCollectionById(id) ?: return null
        val folders = collectionDao.getFoldersForCollection(id)
        return CollectionMapper.mapEntityToDomain(entity, folders)
    }

    override suspend fun getRequestById(id: String): SavedApiRequest? =
        collectionDao.getRequestById(id)?.let(RequestMapper::mapEntityToDomain)

    override suspend fun saveCollection(collection: ApiCollection) {
        val entity = CollectionMapper.mapDomainToEntity(collection)
        collectionDao.insertCollection(entity)
    }

    override suspend fun deleteCollection(collectionId: String) {
        collectionDao.deleteCollectionCascadeTx(collectionId)
    }

    override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {
        val entity = CollectionFolderEntity(
            id = folder.id,
            collectionId = collectionId,
            name = folder.name,
            isExpanded = folder.isExpanded
        )
        collectionDao.insertFolder(entity)
    }

    override suspend fun deleteFolder(folderId: String) {
        collectionDao.deleteFolder(folderId)
    }

    override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
        val entity = RequestMapper.mapDomainToEntity(request, collectionId, folderId)
        collectionDao.insertRequest(entity)
    }

    override suspend fun deleteRequest(requestId: String) {
        collectionDao.deleteRequest(requestId)
    }

    override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> {
        return collectionDao.getRequestsForCollectionFlow(UNSAVED_COLLECTION_ID).map { entities ->
            entities.map { RequestMapper.mapEntityToDomain(it) }
        }
    }

    override suspend fun saveUnsavedRequest(request: SavedApiRequest) {
        val entity = RequestMapper.mapDomainToEntity(request, UNSAVED_COLLECTION_ID)
        collectionDao.insertRequest(entity)
    }

    override suspend fun deleteUnsavedRequest(requestId: String) {
        collectionDao.deleteRequest(requestId)
    }

    override suspend fun saveUnsavedToNewCollectionTx(
        collection: ApiCollection,
        folder: CollectionFolder,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    ) {
        val collectionEntity = CollectionMapper.mapDomainToEntity(collection)
        val folderEntity = CollectionFolderEntity(
            id = folder.id,
            collectionId = collection.id,
            name = folder.name,
            isExpanded = folder.isExpanded
        )
        val requestEntity = RequestMapper.mapDomainToEntity(request, collection.id, folder.id)

        collectionDao.saveUnsavedToNewCollectionTx(
            collection = collectionEntity,
            folder = folderEntity,
            request = requestEntity,
            unsavedRequestIdToDelete = unsavedRequestIdToDelete
        )
    }

    override suspend fun saveUnsavedToExistingCollectionTx(
        collectionId: String,
        folderId: String,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    ) {
        collectionDao.saveUnsavedToExistingCollectionTx(
            request = RequestMapper.mapDomainToEntity(request, collectionId, folderId),
            unsavedRequestIdToDelete = unsavedRequestIdToDelete
        )
    }
}
