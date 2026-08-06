package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe manager responsible for tracking active asynchronous HTTP request/response suspensions.
 */
object InterceptSessionManager {

    private val activeSuspensions = ConcurrentHashMap<String, InterceptedEvent>()

    /**
     * Creates and registers a suspension event for an inbound request.
     */
    fun suspendRequest(request: HttpRequest): InterceptedEvent {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<InterceptResult>()
        val event = InterceptedEvent(
            id = id,
            phase = BreakpointPhase.REQUEST,
            method = request.method,
            url = request.url,
            request = request,
            response = null,
            deferred = deferred
        )
        activeSuspensions[id] = event
        return event
    }

    /**
     * Creates and registers a suspension event for an outbound response.
     */
    fun suspendResponse(request: HttpRequest, response: HttpResponse): InterceptedEvent {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<InterceptResult>()
        val event = InterceptedEvent(
            id = id,
            phase = BreakpointPhase.RESPONSE,
            method = request.method,
            url = request.url,
            request = request,
            response = response,
            deferred = deferred
        )
        activeSuspensions[id] = event
        return event
    }

    /**
     * Resumes a suspended connection event by resolving its deferred completion handle.
     */
    fun resume(eventId: String, result: InterceptResult): Boolean {
        val event = activeSuspensions.remove(eventId) ?: return false
        return event.deferred.complete(result)
    }

    /**
     * Returns a snapshot list of all currently suspended connection events.
     */
    fun getActiveEvents(): List<InterceptedEvent> {
        return activeSuspensions.values.toList()
    }

    /**
     * Obtains an active suspension event by its ID.
     */
    fun getActiveEvent(eventId: String): InterceptedEvent? {
        return activeSuspensions[eventId]
    }

    /**
     * Clears and drops all active suspensions immediately.
     */
    fun clearSuspensions() {
        activeSuspensions.keys.forEach { id ->
            resume(id, InterceptResult.Drop)
        }
    }
}
