package com.devuloopers.knet.data.desktop.traffic.repository

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.data.desktop.mapper.TransactionMapper
import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.traffic.model.TransactionBody
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository
import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop implementation of [LiveTrafficRepository].
 *
 * The [transactionsFlow] emits metadata-only transaction records — body fields are always null
 * to avoid disk I/O during live table rendering. Raw payload bytes are read from disk lazily
 * via [loadTransactionBody] only when the inspector panel opens for a selected row.
 */
public class LiveTrafficRepositoryImpl(
    private val database: KNetDatabase
) : LiveTrafficRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    override val transactionsFlow: Flow<List<HttpTransaction>> = database.httpTransactionDao().getAllTransactionsFlow().map { entities ->
        KNetLogger.info(tag = LogTags.TRAFFIC) {
            "ROOM FLOW EMITTED: ${entities.size} transaction records from HttpTransactionDao"
        }
        // mapEntityToDomain sets body = null — no disk I/O in this hot path.
        entities.map { TransactionMapper.mapEntityToDomain(it) }
    }

    override suspend fun getTransactionById(transactionId: String): HttpTransaction? {
        return withContext(Dispatchers.IO) {
            database.httpTransactionDao().getTransactionById(transactionId)?.let { entity ->
                TransactionMapper.mapEntityToDomain(entity)
            }
        }
    }

    /**
     * Loads raw request and response body payloads for the specified transaction on-demand.
     * Reads payload files from disk — must be called from a background dispatcher.
     *
     * @param transactionId Unique UUID of the target transaction.
     * @return [TransactionBody] with raw bytes and headers for decoding, or [TransactionBody.Empty]
     *         if the entity or its payload files cannot be resolved.
     */
    override suspend fun loadTransactionBody(transactionId: String): TransactionBody {
        return withContext(Dispatchers.IO) {
            val entity = database.httpTransactionDao().getTransactionById(transactionId)
            if (entity == null) {
                KNetLogger.warn("LiveTrafficRepository") { "[LOAD BODY] Transaction entity null for id=$transactionId" }
                return@withContext TransactionBody.Empty
            }

            val requestHeaders = parseHeadersString(entity.requestHeadersJson)
            val responseHeaders = parseHeadersString(entity.responseHeadersJson ?: "")
            val reqBody = readBodyFromPath(entity.requestBodyPath)
            val resBody = readBodyFromPath(entity.responseBodyPath)

            TransactionBody(
                requestBody = reqBody,
                requestHeaders = requestHeaders,
                responseBody = resBody,
                responseHeaders = responseHeaders
            )
        }
    }

    /**
     * Persists a synthetic [HttpTransaction] record to the Room DB so that API Studio
     * direct (non-proxy) requests and failures appear in the live Traffic feed.
     *
     * Runs on [Dispatchers.IO] — safe to call from any coroutine context.
     */
    override suspend fun recordTransaction(transaction: HttpTransaction) {
        withContext(Dispatchers.IO) {
            try {
                val entity = TransactionMapper.mapDomainToEntity(transaction)
                database.httpTransactionDao().insert(entity)
                KNetLogger.info(tag = "KNet_Traffic_Record") {
                    "[DIRECT RECORD] [id=${transaction.id}]: ${transaction.request.method} ${transaction.request.url} → ${transaction.response?.statusCode}"
                }
            } catch (e: Exception) {
                KNetLogger.error(tag = "KNet_Traffic_Record", throwable = e) {
                    "Failed to record direct transaction: ${e.message}"
                }
            }
        }
    }

    override fun clearSession() {
        scope.launch {
            database.httpTransactionDao().clearAll()
        }
    }

    private fun parseHeadersString(headersJson: String): List<Pair<String, String>> {
        if (headersJson.isBlank()) return emptyList()
        val trimmed = headersJson.trim()
        if (trimmed.startsWith("[")) {
            return com.devuloopers.knet.engine.session.HttpTransactionMapper.deserializeHeaders(trimmed)
        }
        return trimmed.split(";\n")
            .filter { it.contains(":") }
            .map { line ->
                val parts = line.split(":", limit = 2)
                parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
            }
    }

    private fun readBodyFromPath(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        return if (file.exists() && file.length() > 0) {
            try {
                file.readBytes()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
}
