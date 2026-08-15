package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

/**
 * Thread-safe single source of truth manager responsible for tracking active asynchronous HTTP request/response suspensions.
 * Utilizes an atomic, lock-free, FIFO-preserving [MutableStateFlow] of immutable lists to guarantee strict arrival
 * ordering, eliminate race conditions, and provide pure Kotlin Multiplatform execution without blocking Netty event loops.
 */
object InterceptSessionManager {

    private val _activeEventsStream = MutableStateFlow<List<InterceptedEvent>>(emptyList())

    /**
     * Cold-to-hot reactive state stream emitting snapshots of active in-flight suspensions in FIFO order.
     */
    val activeEventsStream: StateFlow<List<InterceptedEvent>> = _activeEventsStream.asStateFlow()

    /**
     * Creates and registers a suspension event for an inbound request in FIFO order.
     *
     * @param request Inbound [HttpRequest] to suspend.
     * @return Generated [InterceptedEvent] containing deferred completion handle.
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
        _activeEventsStream.update { currentList ->
            currentList + event
        }
        return event
    }

    /**
     * Creates and registers a suspension event for an outbound response in FIFO order.
     *
     * @param request Original [HttpRequest] associated with the response.
     * @param response Outbound [HttpResponse] to suspend.
     * @return Generated [InterceptedEvent] containing deferred completion handle.
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
        _activeEventsStream.update { currentList ->
            currentList + event
        }
        return event
    }

    /**
     * Resumes a suspended connection event by atomically removing it from the queue and resolving its deferred handle.
     *
     * @param eventId Unique identifier of the suspension event.
     * @param result Interception resolution outcome ([InterceptResult.Resume], [InterceptResult.Drop], or [InterceptResult.Timeout]).
     * @return True if the event was found and its deferred handle completed; false if already completed or expired.
     */
    fun resume(eventId: String, result: InterceptResult): Boolean {
        var targetEvent: InterceptedEvent? = null
        _activeEventsStream.update { currentList ->
            val found = currentList.find { it.id == eventId }
            if (found != null) {
                targetEvent = found
                currentList.filterNot { it.id == eventId }
            } else {
                currentList
            }
        }
        return targetEvent?.deferred?.complete(result) ?: false
    }

    /**
     * Returns a snapshot list of all currently suspended connection events in strict FIFO order.
     */
    fun getActiveEvents(): List<InterceptedEvent> {
        return _activeEventsStream.value
    }

    /**
     * Obtains an active suspension event by its unique ID, or null if not found.
     *
     * @param eventId Unique identifier of the suspension event.
     */
    fun getActiveEvent(eventId: String): InterceptedEvent? {
        return _activeEventsStream.value.find { it.id == eventId }
    }

    /**
     * Atomically clears and drops all active suspensions immediately.
     */
    fun clearSuspensions() {
        var previousEvents: List<InterceptedEvent> = emptyList()
        _activeEventsStream.update { currentList ->
            previousEvents = currentList
            emptyList()
        }
        previousEvents.forEach { event ->
            event.deferred.complete(InterceptResult.Drop)
        }
    }
}

