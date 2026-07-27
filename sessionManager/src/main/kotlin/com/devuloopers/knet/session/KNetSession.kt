package com.devuloopers.knet.session

import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import com.devuloopers.knet.model.HttpTransaction
import com.devuloopers.knet.session.util.HttpTransactionMapper
import com.devuloopers.knet.storage.KNetDatabase
import com.devuloopers.knet.storage.HttpTransactionEntity
import com.devuloopers.knet.logger.KNetLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "KNetSession"

/**
 * Manages the active workspace session capture buffer, SQLite sync database,
 * and binary payload persistence on disk.
 *
 * @property database The SQLite metadata database handle.
 * @property payloadStore The cache folder manager for raw request/response body payloads.
 */
class KNetSession(
    private val database: KNetDatabase,
    private val payloadStore: FilePayloadStore
) {

    companion object {
        /**
         * Maximum number of transactions persisted in the database.
         * When this limit is exceeded after a new request is recorded,
         * the oldest transactions and their disk payload files are pruned automatically.
         */
        const val MAX_PERSISTED_TRANSACTIONS = 1000
    }

    private val transactionDao = database.httpTransactionDao()

    /**
     * Cold stream returning the chronologically descending transactions list.
     * Automatically maps database updates to domain DTOs.
     */
    val transactionsFlow: Flow<List<HttpTransaction>> = transactionDao
        .getAllTransactionsFlow()
        .map { list ->
            list.map { HttpTransactionMapper.toDomainModel(it, payloadStore) }
        }

    /**
     * Records a new client request, saves its body to disk, and persists metadata to database.
     *
     * @param request The captured request model.
     * @return The domain [HttpTransaction] representation.
     */
    suspend fun recordRequest(request: HttpRequest): HttpTransaction {
        val requestBodyPath = payloadStore.savePayload(request.id, "req", request.body)
        val entity = HttpTransactionEntity(
            id = request.id,
            url = request.url,
            method = request.method,
            requestHeadersJson = HttpTransactionMapper.serializeHeaders(request.headers),
            requestBodyPath = requestBodyPath,
            responseStatusCode = null,
            responseStatusText = null,
            responseHeadersJson = null,
            responseBodyPath = null,
            durationMs = 0,
            timestamp = request.timestamp
        )
        transactionDao.insert(entity)

        // Enforce sliding transaction limit after each insert
        pruneIfLimitExceeded()

        return HttpTransactionMapper.toDomainModel(entity, payloadStore)
    }

    /**
     * Records a server response for an existing transaction, saves its body to disk, and updates database.
     *
     * @param transactionId The matching transaction ID.
     * @param response The captured response model.
     * @param durationMs Request execution latency.
     * @return True if the update successfully completed.
     */
    suspend fun recordResponse(
        transactionId: String,
        response: HttpResponse,
        durationMs: Long,
        timings: com.devuloopers.knet.model.HttpTimings = com.devuloopers.knet.model.HttpTimings()
    ): Boolean {
        val entity = transactionDao.getTransactionById(transactionId) ?: return false
        val responseBodyPath = payloadStore.savePayload(transactionId, "res", response.body)
        
        val updated = HttpTransactionEntity(
            id = entity.id,
            url = entity.url,
            method = entity.method,
            requestHeadersJson = entity.requestHeadersJson,
            requestBodyPath = entity.requestBodyPath,
            responseStatusCode = response.statusCode,
            responseStatusText = response.statusText,
            responseHeadersJson = HttpTransactionMapper.serializeHeaders(response.headers),
            responseBodyPath = responseBodyPath,
            durationMs = durationMs,
            timestamp = entity.timestamp,
            timingDnsMs = timings.dnsMs,
            timingTcpMs = timings.tcpMs,
            timingTlsMs = timings.tlsMs,
            timingTtfbMs = timings.ttfbMs,
            timingDownloadMs = timings.downloadMs
        )
        transactionDao.insert(updated)
        return true
    }

    /**
     * Clears all transactions and payload files.
     */
    suspend fun clearSession() {
        transactionDao.clearAll()
        payloadStore.clearStore()
    }

    /**
     * Enforces the [MAX_PERSISTED_TRANSACTIONS] sliding limit.
     *
     * When the total transaction count exceeds the limit, the oldest
     * excess records are identified, their corresponding disk payload
     * files (both request and response) are deleted, and the database
     * rows are batch-removed in a single query.
     */
    private suspend fun pruneIfLimitExceeded() {
        val count = transactionDao.getTransactionCount()
        if (count <= MAX_PERSISTED_TRANSACTIONS) return

        val excess = count - MAX_PERSISTED_TRANSACTIONS
        val oldestEntities = transactionDao.getOldestTransactions(excess)

        if (oldestEntities.isEmpty()) return

        KNetLogger.info(TAG) {
            "Pruning $excess oldest transactions (total: $count, limit: $MAX_PERSISTED_TRANSACTIONS)"
        }

        // Delete disk payload files for each pruned transaction
        oldestEntities.forEach { entity ->
            payloadStore.deletePayload(entity.requestBodyPath)
            payloadStore.deletePayload(entity.responseBodyPath)
        }

        // Batch-delete database rows
        val idsToDelete = oldestEntities.map { it.id }
        transactionDao.deleteTransactionsByIds(idsToDelete)
    }
}
