package com.devuloopers.knet.domain.rules.repository

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.rules.model.InterceptedTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract managing active in-flight network traffic suspensions.
 * Decouples presentation and domain layers from low-level Netty socket handlers and concurrency primitives.
 */
public interface InterceptionSessionRepository {

    /**
     * Cold stream emitting the current list of active in-flight suspended transactions.
     */
    val activeInterceptions: Flow<List<InterceptedTransaction>>

    /**
     * Forwards an intercepted request to the upstream target server with optional payload modifications.
     *
     * @param transactionId Unique ID of the suspended interception event.
     * @param modifiedRequest The modified or original [HttpRequest] to forward.
     */
    suspend fun forwardRequest(transactionId: String, modifiedRequest: HttpRequest)

    /**
     * Forwards an intercepted response back to the client with optional payload modifications.
     *
     * @param transactionId Unique ID of the suspended interception event.
     * @param modifiedResponse The modified or original [HttpResponse] to forward.
     */
    suspend fun forwardResponse(transactionId: String, modifiedResponse: HttpResponse)

    /**
     * Drops and closes an intercepted in-flight connection.
     *
     * @param transactionId Unique ID of the suspended interception event.
     */
    suspend fun dropTransaction(transactionId: String)

    /**
     * Drops and clears all active in-flight suspensions immediately.
     */
    suspend fun clearAll()
}
