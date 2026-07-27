package com.devuloopers.knet.storage.interception

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) defining SQLite database operations for [HttpTransactionEntity].
 */
@Dao
interface HttpTransactionDao {

    /**
     * Inserts or replaces an HTTP transaction entity.
     *
     * @param transaction The transaction data to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: HttpTransactionEntity)

    /**
     * Streams all persisted HTTP transaction records sorted chronologically descending.
     *
     * @return A cold reactive Flow containing lists of transaction entities.
     */
    @Query("SELECT * FROM HttpTransactionEntity ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<HttpTransactionEntity>>

    /**
     * Retrieves a single transaction record by its unique identifier.
     *
     * @param id The transaction ID.
     * @return The transaction entity if found, or null.
     */
    @Query("SELECT * FROM HttpTransactionEntity WHERE id = :id")
    suspend fun getTransactionById(id: String): HttpTransactionEntity?

    /**
     * Clears all captured transaction records from the database.
     */
    @Query("DELETE FROM HttpTransactionEntity")
    suspend fun clearAll()

    /**
     * Returns the total number of persisted transaction records.
     * Used by session pruning to detect when the limit is exceeded.
     *
     * @return The count of rows in the HttpTransactionEntity table.
     */
    @Query("SELECT COUNT(*) FROM HttpTransactionEntity")
    suspend fun getTransactionCount(): Int

    /**
     * Retrieves the oldest transaction records sorted by timestamp ascending.
     * Used by session pruning to identify records eligible for deletion.
     *
     * @param count The maximum number of oldest records to return.
     * @return A list of the oldest [HttpTransactionEntity] records.
     */
    @Query("SELECT * FROM HttpTransactionEntity ORDER BY timestamp ASC LIMIT :count")
    suspend fun getOldestTransactions(count: Int): List<HttpTransactionEntity>

    /**
     * Batch-deletes transaction records whose IDs match the provided list.
     * Used by session pruning to remove expired records in a single query.
     *
     * @param ids The list of transaction IDs to delete.
     */
    @Query("DELETE FROM HttpTransactionEntity WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<String>)
}
