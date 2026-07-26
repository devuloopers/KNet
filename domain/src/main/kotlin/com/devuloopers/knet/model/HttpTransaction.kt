package com.devuloopers.knet.model

/**
 * Data transfer object encapsulating a complete HTTP request/response transaction.
 *
 * @property id Unique transaction tracing identifier.
 * @property request Mapped client HTTP request.
 * @property response Mapped target HTTP response, or null if dropped or timed out.
 * @property requestBodyPath Path to cached request payload body file on disk, or null.
 * @property responseBodyPath Path to cached response payload body file on disk, or null.
 * @property durationMs Execution latency of the transaction.
 * @property timestamp Epoch millisecond timestamp of request initiation.
 */
data class HttpTransaction(
    val id: String,
    val request: HttpRequest,
    val response: HttpResponse?,
    val requestBodyPath: String?,
    val responseBodyPath: String?,
    val durationMs: Long,
    val timestamp: Long,
    val timings: HttpTimings = HttpTimings()
)
