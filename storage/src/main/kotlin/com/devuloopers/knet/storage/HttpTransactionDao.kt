package com.devuloopers.knet.storage

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
}
