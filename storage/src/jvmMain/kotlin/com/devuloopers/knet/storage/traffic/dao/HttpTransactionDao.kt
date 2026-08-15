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
interface HttpTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: HttpTransactionEntity)

    @Query("SELECT * FROM HttpTransactionEntity ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<HttpTransactionEntity>>

    @Query("SELECT * FROM HttpTransactionEntity WHERE id = :id")
    suspend fun getTransactionById(id: String): HttpTransactionEntity?

    @Query("DELETE FROM HttpTransactionEntity")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM HttpTransactionEntity")
    suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM HttpTransactionEntity ORDER BY timestamp ASC LIMIT :count")
    suspend fun getOldestTransactions(count: Int): List<HttpTransactionEntity>

    @Query("DELETE FROM HttpTransactionEntity WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<String>)

    @Query(
        """
        UPDATE HttpTransactionEntity SET
            responseStatusCode = :statusCode,
            responseStatusText = :statusText,
            responseHeadersJson = :responseHeadersJson,
            responseBodyPath = :responseBodyPath,
            responseBodySize = :responseBodySize,
            durationMs = :durationMs,
            timingDnsMs = :timingDnsMs,
            timingTcpMs = :timingTcpMs,
            timingTlsMs = :timingTlsMs,
            timingTtfbMs = :timingTtfbMs,
            timingDownloadMs = :timingDownloadMs
        WHERE id = :id
    """
    )
    suspend fun updateResponse(
        id: String,
        statusCode: Int,
        statusText: String,
        responseHeadersJson: String?,
        responseBodyPath: String?,
        responseBodySize: Long,
        durationMs: Long,
        timingDnsMs: Long,
        timingTcpMs: Long,
        timingTlsMs: Long,
        timingTtfbMs: Long,
        timingDownloadMs: Long
    )
}
