package com.devuloopers.knet.storage.apistudio.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.devuloopers.knet.storage.apistudio.entity.ApiStudioProtocolSchemaEntity
import com.devuloopers.knet.storage.apistudio.entity.ApiStudioWorkspaceDocumentEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import kotlinx.coroutines.flow.Flow

/** Bounded persistence operations for additive API Studio editor contributions. */
@Dao
interface ProtocolDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspaceDocument(document: ApiStudioWorkspaceDocumentEntity)

    @Query("SELECT * FROM api_studio_workspace_documents ORDER BY updatedAtEpochMillis DESC, id DESC")
    fun observeWorkspaceDocuments(): Flow<List<ApiStudioWorkspaceDocumentEntity>>

    @Query("SELECT * FROM api_studio_workspace_documents WHERE id = :id LIMIT 1")
    suspend fun workspaceDocument(id: String): ApiStudioWorkspaceDocumentEntity?

    @Query("DELETE FROM api_studio_workspace_documents WHERE id = :id")
    suspend fun deleteWorkspaceDocument(id: String): Int

    @Query(
        """
        UPDATE api_studio_workspace_documents
        SET requestKind = :requestKind,
            name = CASE WHEN nameOrigin = 'USER_DEFINED' THEN name ELSE :suggestedName END,
            badgeLabel = :badgeLabel,
            payloadVersion = :payloadVersion,
            payload = :payload,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND editorId = :editorId
        """,
    )
    suspend fun updateWorkspaceContent(
        id: String,
        editorId: String,
        requestKind: String,
        suggestedName: String,
        badgeLabel: String,
        payloadVersion: Int,
        payload: ByteArray,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE api_studio_workspace_documents
        SET name = :name,
            nameOrigin = 'USER_DEFINED',
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun renameWorkspaceDocument(id: String, name: String, updatedAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE api_studio_workspace_documents
        SET name = :name,
            nameOrigin = :nameOrigin,
            collectionId = :collectionId,
            folderId = :folderId,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
        """,
    )
    suspend fun promoteWorkspaceDocument(
        id: String,
        name: String,
        nameOrigin: String,
        collectionId: String,
        folderId: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: CollectionFolderEntity)

    @Transaction
    suspend fun promoteWorkspaceDocumentToNewCollection(
        documentId: String,
        documentName: String,
        nameOrigin: String,
        collection: CollectionEntity,
        folder: CollectionFolderEntity,
        updatedAtEpochMillis: Long,
    ): Int {
        insertCollection(collection)
        insertFolder(folder)
        return promoteWorkspaceDocument(
            id = documentId,
            name = documentName,
            nameOrigin = nameOrigin,
            collectionId = collection.id,
            folderId = folder.id,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchema(schema: ApiStudioProtocolSchemaEntity)

    @Query(
        """
        SELECT * FROM api_studio_protocol_schemas
        WHERE kind = :kind AND sourceId = :sourceId
        LIMIT 1
        """,
    )
    suspend fun schema(kind: String, sourceId: String): ApiStudioProtocolSchemaEntity?
}
