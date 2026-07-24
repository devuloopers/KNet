package com.devuloopers.knet.interceptor

import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe manager responsible for tracking breakpoint rules and managing
 * active asynchronous HTTP request/response suspensions.
 */
object BreakpointManager {

    private val rules = ConcurrentHashMap<String, BreakpointRule>()
    private val activeSuspensions = ConcurrentHashMap<String, InterceptedEvent>()

    /**
     * Adds an interception breakpoint rule.
     *
     * @param rule The rule config to add.
     */
    fun addRule(rule: BreakpointRule) {
        rules[rule.id] = rule
    }

    /**
     * Removes an interception breakpoint rule by identifier.
     *
     * @param ruleId The ID of the rule to remove.
     */
    fun removeRule(ruleId: String) {
        rules.remove(ruleId)
    }

    /**
     * Clears all active breakpoint rules.
     */
    fun clearRules() {
        rules.clear()
    }

    /**
     * Checks if a request should be intercepted based on active rules.
     *
     * @param url The full URL of the request.
     * @param method The HTTP method of the request.
     * @return The matching [BreakpointRule] if request interception is enabled, or null.
     */
    fun findMatchingRequestRule(url: String, method: String): BreakpointRule? {
        return rules.values.firstOrNull { it.isRequestEnabled && it.matches(url, method) }
    }

    /**
     * Checks if a response should be intercepted based on active rules.
     *
     * @param url The full URL of the corresponding request.
     * @param method The HTTP method of the request.
     * @return The matching [BreakpointRule] if response interception is enabled, or null.
     */
    fun findMatchingResponseRule(url: String, method: String): BreakpointRule? {
        return rules.values.firstOrNull { it.isResponseEnabled && it.matches(url, method) }
    }

    /**
     * Creates and registers a suspension event for an inbound request.
     *
     * @param request The captured request model.
     * @return The registered [InterceptedEvent].
     */
    fun suspendRequest(request: HttpRequest): InterceptedEvent {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<InterceptResult>()
        val event = InterceptedEvent(id, request, null, deferred)
        activeSuspensions[id] = event
        return event
    }

    /**
     * Creates and registers a suspension event for an outbound response.
     *
     * @param request The captured request model.
     * @param response The captured response model.
     * @return The registered [InterceptedEvent].
     */
    fun suspendResponse(request: HttpRequest, response: HttpResponse): InterceptedEvent {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<InterceptResult>()
        val event = InterceptedEvent(id, request, response, deferred)
        activeSuspensions[id] = event
        return event
    }

    /**
     * Resumes a paused connection by resolving its deferred completion value.
     *
     * @param eventId The unique identifier of the suspension event.
     * @param result The outcome to resume with (Resume or Drop).
     * @return True if the event was found and successfully resumed.
     */
    fun resume(eventId: String, result: InterceptResult): Boolean {
        val event = activeSuspensions.remove(eventId) ?: return false
        return event.deferred.complete(result)
    }

    /**
     * Returns a list of all currently suspended connection events.
     *
     * @return A list of active [InterceptedEvent] objects.
     */
    fun getActiveEvents(): List<InterceptedEvent> {
        return activeSuspensions.values.toList()
    }

    /**
     * Retrieves a suspended connection event by its identifier.
     *
     * @param eventId The unique identifier of the suspension event.
     * @return The active [InterceptedEvent], or null if not found.
     */
    fun getActiveEvent(eventId: String): InterceptedEvent? {
        return activeSuspensions[eventId]
    }

    /**
     * Clears all active suspensions, dropping them immediately.
     */
    fun clearSuspensions() {
        activeSuspensions.keys.forEach { id ->
            resume(id, InterceptResult.Drop)
        }
    }
}
