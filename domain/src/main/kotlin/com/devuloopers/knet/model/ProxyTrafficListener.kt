package com.devuloopers.knet.model

/**
 * Interface to receive captured HTTP requests and responses from the proxy engine.
 */
interface ProxyTrafficListener {
    /**
     * Invoked when a client request has been captured and decoded.
     */
    fun onRequestCaptured(request: HttpRequest)

    /**
     * Invoked when a target server response has been captured and decoded.
     * Includes optional high-resolution socket connection phase timing metrics.
     */
    fun onResponseCaptured(
        transactionId: String,
        response: HttpResponse,
        durationMs: Long,
        timings: HttpTimings = HttpTimings()
    )
}
