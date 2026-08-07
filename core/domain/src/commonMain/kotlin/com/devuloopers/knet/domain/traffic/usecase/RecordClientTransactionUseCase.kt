package com.devuloopers.knet.domain.traffic.usecase

import com.devuloopers.knet.domain.clientNetwork.model.HttpTransaction
import com.devuloopers.knet.domain.traffic.repository.LiveTrafficRepository

/**
 * Use case for recording a direct (non-proxy) API Studio request/response transaction
 * into the live Traffic feed.
 *
 * Bridges the API Studio execution pipeline with the Traffic monitor by persisting
 * synthetic [HttpTransaction] records to Room DB whenever a request is fired directly
 * (i.e., the KNet proxy engine is stopped or not routing the request).
 *
 * @param repository The [LiveTrafficRepository] instance used for transaction persistence.
 */
public class RecordClientTransactionUseCase(
    private val repository: LiveTrafficRepository
) {
    /**
     * Persists the given [transaction] to the Traffic feed database.
     *
     * @param transaction The fully-formed [HttpTransaction] to record.
     */
    public suspend fun execute(transaction: HttpTransaction) {
        repository.recordTransaction(transaction)
    }
}
