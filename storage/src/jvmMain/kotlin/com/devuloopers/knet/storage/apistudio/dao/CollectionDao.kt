package com.devuloopers.knet.storage.apistudio.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for API collections, folders, and saved requests.
 */
@Dao
interface CollectionDao {

    @Query("SELECT * FROM api_collections ORDER BY updatedAt DESC")
    fun getAllCollectionsFlow(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM api_collections WHERE id = :id")
    suspend fun getCollectionById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Query("DELETE FROM api_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("DELETE FROM collection_folders WHERE collectionId = :collectionId")
    suspend fun deleteFoldersForCollection(collectionId: String)

    @Query("DELETE FROM saved_requests WHERE collectionId = :collectionId")
    suspend fun deleteRequestsForCollection(collectionId: String)

    @Transaction
    suspend fun deleteCollectionCascadeTx(id: String) {
        deleteCollection(id)
        deleteFoldersForCollection(id)
        deleteRequestsForCollection(id)
    }

    @Query("SELECT * FROM collection_folders ORDER BY orderIndex ASC")
    fun getAllFoldersFlow(): Flow<List<CollectionFolderEntity>>

    @Query("SELECT * FROM saved_requests")
    fun getAllRequestsFlow(): Flow<List<SavedRequestEntity>>

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY orderIndex ASC")
    suspend fun getFoldersForCollection(collectionId: String): List<CollectionFolderEntity>

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY orderIndex ASC")
    fun getFoldersForCollectionFlow(collectionId: String): Flow<List<CollectionFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: CollectionFolderEntity)

    @Query("DELETE FROM collection_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM saved_requests WHERE folderId = :folderId ORDER BY id ASC")
    suspend fun getRequestsForFolder(folderId: String): List<SavedRequestEntity>

    @Query("SELECT * FROM saved_requests WHERE collectionId = :collectionId ORDER BY id ASC")
    suspend fun getRequestsForCollection(collectionId: String): List<SavedRequestEntity>

    @Query("SELECT * FROM saved_requests WHERE collectionId = :collectionId ORDER BY id ASC")
    fun getRequestsForCollectionFlow(collectionId: String): Flow<List<SavedRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: SavedRequestEntity)

    @Query("DELETE FROM saved_requests WHERE id = :id")
    suspend fun deleteRequest(id: String)

    @Transaction
    suspend fun saveUnsavedToNewCollectionTx(
        collection: CollectionEntity,
        folder: CollectionFolderEntity,
        request: SavedRequestEntity,
        unsavedRequestIdToDelete: String
    ) {
        insertCollection(collection)
        insertFolder(folder)
        insertRequest(request)
        deleteRequest(unsavedRequestIdToDelete)
    }
}
