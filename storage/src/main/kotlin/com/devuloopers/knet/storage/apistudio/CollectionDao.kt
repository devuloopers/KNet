package com.devuloopers.knet.storage.apistudio

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for API collections, folders, and saved requests.
 */
@Dao
interface CollectionDao {

    // ── Collections CRUD ────────────────────────────────────────────────────

    @Query("SELECT * FROM api_collections ORDER BY updatedAt DESC")
    fun getAllCollectionsFlow(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM api_collections WHERE id = :id")
    suspend fun getCollectionById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Query("DELETE FROM api_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    // ── Folders CRUD ────────────────────────────────────────────────────────

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY orderIndex ASC")
    suspend fun getFoldersForCollection(collectionId: String): List<CollectionFolderEntity>

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY orderIndex ASC")
    fun getFoldersForCollectionFlow(collectionId: String): Flow<List<CollectionFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: CollectionFolderEntity)

    @Query("DELETE FROM collection_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    // ── Saved Requests CRUD ─────────────────────────────────────────────────

    @Query("SELECT * FROM saved_requests WHERE folderId = :folderId")
    suspend fun getRequestsForFolder(folderId: String): List<SavedRequestEntity>

    @Query("SELECT * FROM saved_requests WHERE collectionId = :collectionId")
    suspend fun getRequestsForCollection(collectionId: String): List<SavedRequestEntity>

    @Query("SELECT * FROM saved_requests WHERE collectionId = :collectionId")
    fun getRequestsForCollectionFlow(collectionId: String): Flow<List<SavedRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: SavedRequestEntity)

    @Query("DELETE FROM saved_requests WHERE id = :id")
    suspend fun deleteRequest(id: String)
}
