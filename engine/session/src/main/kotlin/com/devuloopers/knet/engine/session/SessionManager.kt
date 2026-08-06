package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.model.SessionStatistics
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Public entry point orchestrating live transaction recording, storage, payload caching, and sliding window buffer pruning.
 *
 * @property database SQLite Room metadata database instance.
 * @property payloadStore File payload store manager for raw request/response body payloads.
 */
class SessionManager(
    private val database: KNetDatabase,
    private val payloadStore: FilePayloadStore,
    val stats: SessionStatistics = SessionStatistics()
) {

    private val transactionDao = database.httpTransactionDao()
    private val recorder = TransactionRecorder(transactionDao, payloadStore, stats)
    private val buffer = SessionBuffer(transactionDao, payloadStore)

    /**
     * Cold stream emitting chronologically descending transaction lists.
     */
    val transactionsFlow: Flow<List<HttpTransaction>> = transactionDao
        .getAllTransactionsFlow()
        .map { list ->
            list.map { HttpTransactionMapper.toDomainModel(it, payloadStore) }
        }

    /**
     * Records a new client request, saves its body payload to disk, and persists metadata to database.
     */
    suspend fun recordRequest(request: HttpRequest): HttpTransaction {
        val transaction = recorder.recordRequest(request)
        buffer.pruneIfLimitExceeded()
        return transaction
    }

    /**
     * Records a server response for an existing transaction, saves its body payload to disk, and updates database.
     */
    suspend fun recordResponse(
        transactionId: String,
        response: HttpResponse,
        durationMs: Long,
        timings: HttpTimings = HttpTimings()
    ): Boolean {
        return recorder.recordResponse(transactionId, response, durationMs, timings)
    }

    /**
     * Clears all transactions from database and deletes all cached payload files from disk.
     */
    suspend fun clearSession() {
        transactionDao.clearAll()
        payloadStore.clearStore()
        stats.reset()
    }
}

/**
 * Type alias for backward compatibility.
 */
typealias KNetSession = SessionManager
