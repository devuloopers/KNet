package com.devuloopers.knet.interceptor

import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import kotlinx.coroutines.CompletableDeferred

/**
 * Result of resolving a suspended intercepted event.
 */
sealed class InterceptResult {
    /**
     * Resumes the traffic pipeline, optionally using modified request/response details.
     *
     * @property modifiedRequest Optional edited request model.
     * @property modifiedResponse Optional edited response model.
     */
    class Resume(val modifiedRequest: HttpRequest?, val modifiedResponse: HttpResponse?) : InterceptResult()

    /**
     * Terminated status indicating that the connection should be dropped immediately.
     */
    object Drop : InterceptResult()
}

/**
 * Encapsulates the context of a connection paused by an active breakpoint.
 *
 * @property id Unique identifier of this suspension event.
 * @property request The captured HTTP request metadata.
 * @property response The captured HTTP response metadata (null if paused during request inbound phase).
 * @property deferred A completable future reference used to resume coroutine execution when resolved.
 */
class InterceptedEvent(
    val id: String,
    val request: HttpRequest,
    val response: HttpResponse?,
    val deferred: CompletableDeferred<InterceptResult>
)
