package com.devuloopers.knet.domain.rules.model

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse

/**
 * Domain model representing an in-flight network transaction suspended by a breakpoint rule.
 * Free of framework or concurrency primitives to guarantee pure multiplatform Clean Architecture.
 *
 * @property id Unique identifier of the interception event.
 * @property phase Traffic phase at which the transaction was intercepted ([BreakpointPhase.REQUEST] or [BreakpointPhase.RESPONSE]).
 * @property method HTTP method (e.g. GET, POST, PUT).
 * @property url Full target request URL.
 * @property request The captured or modified [HttpRequest] payload.
 * @property response The captured or modified [HttpResponse] payload (present if intercepted during response phase).
 * @property timestamp Epoch millisecond timestamp when the transaction was paused.
 */
public data class InterceptedTransaction(
    val id: String,
    val phase: BreakpointPhase,
    val method: String,
    val url: String,
    val request: HttpRequest,
    val response: HttpResponse? = null,
    val timestamp: Long = 0L
)
