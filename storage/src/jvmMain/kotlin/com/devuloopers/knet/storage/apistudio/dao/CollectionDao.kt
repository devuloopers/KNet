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
public interface CollectionDao {

    @Query("SELECT * FROM api_collections ORDER BY updatedAt DESC")
    public fun getAllCollectionsFlow(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM api_collections WHERE id = :id")
    public suspend fun getCollectionById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertCollection(collection: CollectionEntity)

    @Query("DELETE FROM api_collections WHERE id = :id")
    public suspend fun deleteCollection(id: String)

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY orderIndex ASC")
    public suspend fun getFoldersForCollection(collectionId: String): List<CollectionFolderEntity>

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY orderIndex ASC")
    public fun getFoldersForCollectionFlow(collectionId: String): Flow<List<CollectionFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertFolder(folder: CollectionFolderEntity)

    @Query("DELETE FROM collection_folders WHERE id = :id")
    public suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM saved_requests WHERE folderId = :folderId")
    public suspend fun getRequestsForFolder(folderId: String): List<SavedRequestEntity>

    @Query("SELECT * FROM saved_requests WHERE collectionId = :collectionId")
    public suspend fun getRequestsForCollection(collectionId: String): List<SavedRequestEntity>

    @Query("SELECT * FROM saved_requests WHERE collectionId = :collectionId")
    public fun getRequestsForCollectionFlow(collectionId: String): Flow<List<SavedRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertRequest(request: SavedRequestEntity)

    @Query("DELETE FROM saved_requests WHERE id = :id")
    public suspend fun deleteRequest(id: String)

    @Transaction
    public suspend fun saveUnsavedToNewCollectionTx(
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
