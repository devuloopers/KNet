package com.devuloopers.knet.domain.traffic.usecase

import com.devuloopers.knet.domain.traffic.model.TransactionBody
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository

/**
 * Single-shot suspend use case that loads raw request and response body payloads for a
 * specific transaction from disk on-demand.
 *
 * This is the only sanctioned path through which payload bytes may be read from disk.
 * It must only be invoked when the user explicitly selects a transaction row in the
 * Inspector panel, never during live traffic list rendering.
 *
 * @property repository Live traffic repository supplying the on-demand body load operation.
 */
class LoadTransactionBodyUseCase(
    private val repository: LiveTrafficRepository
) {

    /**
     * Executes the on-demand body load for the given transaction ID.
     *
     * @param transactionId Unique UUID of the target transaction.
     * @return [TransactionBody] containing raw byte arrays and header lists for decoding,
     *         or [TransactionBody.Empty] if the transaction or payload files are missing.
     */
    suspend fun execute(transactionId: String): TransactionBody {
        return repository.loadTransactionBody(transactionId)
    }
}
