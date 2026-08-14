package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.InterceptedTransaction
import com.devuloopers.knet.domain.rules.repository.InterceptionSessionRepository
import com.devuloopers.knet.engine.interceptor.InterceptResult
import com.devuloopers.knet.engine.interceptor.InterceptSessionManager
import com.devuloopers.knet.engine.interceptor.InterceptedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Desktop implementation of [InterceptionSessionRepository] bridging domain UseCases
 * to Netty's in-memory [InterceptSessionManager].
 */
public class InterceptionSessionRepositoryImpl(
    private val sessionManager: InterceptSessionManager = InterceptSessionManager
) : InterceptionSessionRepository {

    override val activeInterceptions: Flow<List<InterceptedTransaction>> =
        sessionManager.activeEventsStream.map { events ->
            events.map { it.toDomainModel() }
        }

    override suspend fun forwardRequest(transactionId: String, modifiedRequest: HttpRequest) {
        sessionManager.resume(transactionId, InterceptResult.Resume(modifiedRequest = modifiedRequest))
    }

    override suspend fun forwardResponse(transactionId: String, modifiedResponse: HttpResponse) {
        sessionManager.resume(transactionId, InterceptResult.Resume(modifiedResponse = modifiedResponse))
    }

    override suspend fun dropTransaction(transactionId: String) {
        sessionManager.resume(transactionId, InterceptResult.Drop)
    }

    override suspend fun clearAll() {
        sessionManager.clearSuspensions()
    }

    private fun InterceptedEvent.toDomainModel(): InterceptedTransaction {
        return InterceptedTransaction(
            id = id,
            phase = phase,
            method = method,
            url = url,
            request = request,
            response = response,
            timestamp = request.timestamp
        )
    }
}
