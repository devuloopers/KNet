package com.devuloopers.knet.engine.session

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.storage.traffic.dao.HttpTransactionDao

private const val TAG = "SessionBuffer"

/**
 * Manages the sliding window session transaction buffer.
 * Prunes oldest transactions and associated disk payload files when the limit is exceeded.
 */
class SessionBuffer(
    private val transactionDao: HttpTransactionDao,
    private val payloadStore: FilePayloadStore,
    var maxPersistedTransactions: Int = 1000
) {

    /**
     * Enforces the sliding window limit.
     * Deletes oldest payload files from disk and removes corresponding rows from the database.
     */
    suspend fun pruneIfLimitExceeded() {
        val count = transactionDao.getTransactionCount()
        if (count <= maxPersistedTransactions) return

        val excess = count - maxPersistedTransactions
        val oldestEntities = transactionDao.getOldestTransactions(excess)
        if (oldestEntities.isEmpty()) return

        KNetLogger.info(TAG) {
            "Pruning $excess oldest transactions (total: $count, limit: $maxPersistedTransactions)"
        }

        oldestEntities.forEach { entity ->
            payloadStore.deletePayload(entity.requestBodyPath)
            payloadStore.deletePayload(entity.responseBodyPath)
        }

        val idsToDelete = oldestEntities.map { it.id }
        transactionDao.deleteTransactionsByIds(idsToDelete)
    }
}
