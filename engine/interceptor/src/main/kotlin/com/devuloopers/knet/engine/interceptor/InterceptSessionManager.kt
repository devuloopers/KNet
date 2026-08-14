package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * Thread-safe manager responsible for tracking active asynchronous HTTP request/response suspensions.
 */
object InterceptSessionManager {

    private val activeSuspensions = ConcurrentHashMap<String, InterceptedEvent>()

    private val _activeEventsStream = MutableStateFlow<List<InterceptedEvent>>(emptyList())
    val activeEventsStream: StateFlow<List<InterceptedEvent>> = _activeEventsStream.asStateFlow()

    /**
     * Creates and registers a suspension event for an inbound request.
     */
    fun suspendRequest(request: HttpRequest): InterceptedEvent {
        val id = Uuid.random().toString()
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
        notifyEventsChanged()
        return event
    }

    /**
     * Creates and registers a suspension event for an outbound response.
     */
    fun suspendResponse(request: HttpRequest, response: HttpResponse): InterceptedEvent {
        val id = Uuid.random().toString()
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
        notifyEventsChanged()
        return event
    }

    /**
     * Resumes a suspended connection event by resolving its deferred completion handle.
     */
    fun resume(eventId: String, result: InterceptResult): Boolean {
        val event = activeSuspensions.remove(eventId)
        notifyEventsChanged()
        return event?.deferred?.complete(result) ?: false
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
        notifyEventsChanged()
    }

    private fun notifyEventsChanged() {
        _activeEventsStream.value = getActiveEvents()
    }
}
