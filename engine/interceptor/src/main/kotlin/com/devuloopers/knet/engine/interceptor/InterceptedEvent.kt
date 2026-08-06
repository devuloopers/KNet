package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import kotlinx.coroutines.CompletableDeferred

/**
 * Container holding metadata and completion handles for an active coroutine traffic suspension.
 */
class InterceptedEvent(
    val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val phase: BreakpointPhase,
    val method: String,
    val url: String,
    val request: HttpRequest,
    val response: HttpResponse? = null,
    val deferred: CompletableDeferred<InterceptResult>
)
