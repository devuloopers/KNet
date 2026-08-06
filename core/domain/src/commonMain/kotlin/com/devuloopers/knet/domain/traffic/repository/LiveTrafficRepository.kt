package com.devuloopers.knet.domain.traffic.repository

import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.traffic.model.TransactionBody
import kotlinx.coroutines.flow.Flow

/**
 * Feature repository contract for the Live Traffic feed.
 * Isolates data fetching, session clearing, and proxy flow operations for live traffic.
 *
 * Body payloads are intentionally excluded from the live stream to avoid disk I/O during
 * table rendering. Call [loadTransactionBody] on-demand when the inspector panel is opened.
 */
interface LiveTrafficRepository {

    /**
     * Cold stream returning the chronologically descending HTTP transaction list from DB.
     * Transactions in this stream always have [HttpTransaction.request].body = null and
     * [HttpTransaction.response].body = null — bodies are loaded lazily via [loadTransactionBody].
     */
    val transactionsFlow: Flow<List<HttpTransaction>>

    /**
     * Loads raw request and response body payloads for the specified transaction on-demand.
     * Performs disk I/O and should be called from a background dispatcher.
     *
     * @param transactionId Unique UUID of the target transaction.
     * @return [TransactionBody] with raw byte arrays and header lists, or [TransactionBody.Empty]
     *         if the transaction or body files cannot be resolved.
     */
    suspend fun loadTransactionBody(transactionId: String): TransactionBody

    /**
     * Clears all recorded transaction records and payload storage.
     */
    fun clearSession()
}
