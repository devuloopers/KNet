package com.devuloopers.knet.storage.traffic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) defining SQLite database operations for [HttpTransactionEntity].
 */
@Dao
public interface HttpTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(transaction: HttpTransactionEntity)

    @Query("SELECT * FROM HttpTransactionEntity ORDER BY timestamp DESC")
    public fun getAllTransactionsFlow(): Flow<List<HttpTransactionEntity>>

    @Query("SELECT * FROM HttpTransactionEntity WHERE id = :id")
    public suspend fun getTransactionById(id: String): HttpTransactionEntity?

    @Query("DELETE FROM HttpTransactionEntity")
    public suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM HttpTransactionEntity")
    public suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM HttpTransactionEntity ORDER BY timestamp ASC LIMIT :count")
    public suspend fun getOldestTransactions(count: Int): List<HttpTransactionEntity>

    @Query("DELETE FROM HttpTransactionEntity WHERE id IN (:ids)")
    public suspend fun deleteTransactionsByIds(ids: List<String>)
}
