package com.devuloopers.knet.domain.rules.usecase

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.rules.model.InterceptedTransaction
import com.devuloopers.knet.domain.rules.repository.InterceptionSessionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Domain UseCase that observes active in-flight network transactions paused by breakpoint rules.
 *
 * @property repository Interception session repository contract.
 */
class ObserveActiveInterceptionsUseCase(
    private val repository: InterceptionSessionRepository
) {
    /**
     * Obtains the reactive stream of active paused transactions.
     *
     * @return Flow emitting snapshots of active [InterceptedTransaction] items.
     */
    operator fun invoke(): Flow<List<InterceptedTransaction>> {
        return repository.activeInterceptions
    }

    /**
     * Explicit execution handle for Java/KMP interop.
     */
    fun execute(): Flow<List<InterceptedTransaction>> {
        return repository.activeInterceptions
    }
}

/**
 * Domain UseCase that forwards an in-flight request to the upstream target server with applied edits.
 *
 * @property repository Interception session repository contract.
 */
class ForwardInterceptedRequestUseCase(
    private val repository: InterceptionSessionRepository
) {
    /**
     * Forwards the modified request to resume network routing.
     *
     * @param transactionId Unique ID of the suspended interception event.
     * @param modifiedRequest The modified or original [HttpRequest] to send upstream.
     */
    suspend operator fun invoke(transactionId: String, modifiedRequest: HttpRequest) {
        repository.forwardRequest(transactionId, modifiedRequest)
    }

    /**
     * Explicit execution handle.
     */
    suspend fun execute(transactionId: String, modifiedRequest: HttpRequest) {
        repository.forwardRequest(transactionId, modifiedRequest)
    }
}

/**
 * Domain UseCase that forwards an in-flight response to the client with applied edits.
 *
 * @property repository Interception session repository contract.
 */
class ForwardInterceptedResponseUseCase(
    private val repository: InterceptionSessionRepository
) {
    /**
     * Forwards the modified response back to the client.
     *
     * @param transactionId Unique ID of the suspended interception event.
     * @param modifiedResponse The modified or original [HttpResponse] to return to the client.
     */
    suspend operator fun invoke(transactionId: String, modifiedResponse: HttpResponse) {
        repository.forwardResponse(transactionId, modifiedResponse)
    }

    /**
     * Explicit execution handle.
     */
    suspend fun execute(transactionId: String, modifiedResponse: HttpResponse) {
        repository.forwardResponse(transactionId, modifiedResponse)
    }
}

/**
 * Domain UseCase that drops and terminates an in-flight suspended transaction connection.
 *
 * @property repository Interception session repository contract.
 */
class DropInterceptedTransactionUseCase(
    private val repository: InterceptionSessionRepository
) {
    /**
     * Drops the in-flight connection.
     *
     * @param transactionId Unique ID of the suspended interception event.
     */
    suspend operator fun invoke(transactionId: String) {
        repository.dropTransaction(transactionId)
    }

    /**
     * Drops the in-flight connection matching [url] and [method].
     *
     * @param url Target HTTP request URL.
     * @param method HTTP method.
     */
    suspend operator fun invoke(url: String, method: String) {
        repository.dropMatching(url, method)
    }

    /**
     * Explicit execution handle.
     */
    suspend fun execute(transactionId: String) {
        repository.dropTransaction(transactionId)
    }

    /**
     * Explicit execution handle matching by [url] and [method].
     */
    suspend fun execute(url: String, method: String) {
        repository.dropMatching(url, method)
    }
}

/**
 * Domain UseCase that drops and clears all active in-flight suspended connections.
 *
 * @property repository Interception session repository contract.
 */
class ClearInterceptionSessionsUseCase(
    private val repository: InterceptionSessionRepository
) {
    /**
     * Drops and clears all active suspensions.
     */
    suspend operator fun invoke() {
        repository.clearAll()
    }

    /**
     * Explicit execution handle.
     */
    suspend fun execute() {
        repository.clearAll()
    }
}
