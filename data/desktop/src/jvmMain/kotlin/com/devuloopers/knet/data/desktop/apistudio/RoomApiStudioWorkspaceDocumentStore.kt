package com.devuloopers.knet.data.desktop.apistudio

import com.devuloopers.knet.application.contract.apistudio.ApiStudioDocumentLocation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceContent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocument
import com.devuloopers.knet.application.contract.apistudio.ApiStudioWorkspaceDocumentStore
import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.storage.apistudio.dao.ProtocolDocumentDao
import com.devuloopers.knet.storage.apistudio.entity.ApiStudioWorkspaceDocumentEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room adapter for incomplete, opaque API Studio documents shared by contributed editors. */
class RoomApiStudioWorkspaceDocumentStore(
    private val dao: ProtocolDocumentDao,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ApiStudioWorkspaceDocumentStore {
    override fun observeDocuments(): Flow<List<ApiStudioWorkspaceDocument>> =
        dao.observeWorkspaceDocuments().map { entities -> entities.map(ApiStudioWorkspaceDocumentEntity::toDomain) }

    override suspend fun document(id: String): ApiStudioWorkspaceDocument? =
        dao.workspaceDocument(id)?.toDomain()

    override suspend fun createDocument(document: ApiStudioWorkspaceDocument) {
        val now = nowMillis()
        dao.insertWorkspaceDocument(document.toEntity(createdAtEpochMillis = now, updatedAtEpochMillis = now))
    }

    override suspend fun updateContent(id: String, content: ApiStudioWorkspaceContent) {
        check(
            dao.updateWorkspaceContent(
                id = id,
                editorId = content.editorId.value,
                requestKind = content.requestKind.value,
                suggestedName = content.suggestedName,
                badgeLabel = content.badgeLabel,
                payloadVersion = content.payloadVersion,
                payload = content.copyPayload(),
                updatedAtEpochMillis = nowMillis(),
            ) == 1,
        ) { "API Studio document '$id' no longer exists." }
    }

    override suspend fun deleteDocument(id: String) {
        dao.deleteWorkspaceDocument(id)
    }

    override suspend fun renameDocument(id: String, name: String) {
        require(name.isNotBlank()) { "API Studio document name must not be blank." }
        check(dao.renameWorkspaceDocument(id, name, nowMillis()) == 1) {
            "API Studio document '$id' no longer exists."
        }
    }

    override suspend fun promoteToExistingCollection(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin,
        collectionId: String,
        folderId: String,
    ) {
        require(name.isNotBlank()) { "API Studio document name must not be blank." }
        check(
            dao.promoteWorkspaceDocument(
                id = id,
                name = name,
                nameOrigin = nameOrigin.name,
                collectionId = collectionId,
                folderId = folderId,
                updatedAtEpochMillis = nowMillis(),
            ) == 1,
        ) { "API Studio document '$id' no longer exists." }
    }

    override suspend fun promoteToNewCollection(
        id: String,
        name: String,
        nameOrigin: RequestNameOrigin,
        collection: ApiCollection,
        folder: CollectionFolder,
    ) {
        require(name.isNotBlank()) { "API Studio document name must not be blank." }
        val now = nowMillis()
        check(
            dao.promoteWorkspaceDocumentToNewCollection(
                documentId = id,
                documentName = name,
                nameOrigin = nameOrigin.name,
                collection = CollectionEntity(
                    id = collection.id,
                    name = collection.name,
                    createdAt = now,
                    updatedAt = now,
                ),
                folder = CollectionFolderEntity(
                    id = folder.id,
                    collectionId = collection.id,
                    name = folder.name,
                    isExpanded = folder.isExpanded,
                ),
                updatedAtEpochMillis = now,
            ) == 1,
        ) { "API Studio document '$id' no longer exists." }
    }
}

private fun ApiStudioWorkspaceDocument.toEntity(
    createdAtEpochMillis: Long,
    updatedAtEpochMillis: Long,
): ApiStudioWorkspaceDocumentEntity {
    val savedLocation = location as? ApiStudioDocumentLocation.Collection
    return ApiStudioWorkspaceDocumentEntity(
        id = id,
        editorId = editorId.value,
        requestKind = requestKind.value,
        name = name,
        nameOrigin = nameOrigin.name,
        badgeLabel = badgeLabel,
        payloadVersion = payloadVersion,
        payload = copyPayload(),
        collectionId = savedLocation?.collectionId,
        folderId = savedLocation?.folderId,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun ApiStudioWorkspaceDocumentEntity.toDomain(): ApiStudioWorkspaceDocument {
    val persistedCollectionId = collectionId
    val persistedFolderId = folderId
    require((persistedCollectionId == null) == (persistedFolderId == null)) {
        "API Studio document '$id' has inconsistent collection placement."
    }
    return ApiStudioWorkspaceDocument(
        id = id,
        editorId = ApiStudioEditorId(editorId),
        requestKind = RequestKindId(requestKind),
        name = name,
        nameOrigin = RequestNameOrigin.fromToken(nameOrigin),
        badgeLabel = badgeLabel,
        payloadVersion = payloadVersion,
        payload = payload,
        location = if (persistedCollectionId == null) {
            ApiStudioDocumentLocation.Unsaved
        } else {
            ApiStudioDocumentLocation.Collection(persistedCollectionId, requireNotNull(persistedFolderId))
        },
    )
}
