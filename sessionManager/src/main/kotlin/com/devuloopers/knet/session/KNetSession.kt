package com.devuloopers.knet.session

import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import com.devuloopers.knet.model.HttpTransaction
import com.devuloopers.knet.session.util.HttpTransactionMapper
import com.devuloopers.knet.storage.KNetDatabase
import com.devuloopers.knet.storage.HttpTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    suspend fun recordResponse(transactionId: String, response: HttpResponse, durationMs: Long): Boolean {
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
            timestamp = entity.timestamp
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
}
