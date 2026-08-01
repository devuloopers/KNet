package com.devuloopers.knet.domain.network.model

/**
 * Event listener interface for receiving intercepted HTTP proxy transactions.
 */
interface ProxyTrafficListener {
    /**
     * Triggered when an HTTP request is captured by the proxy engine.
     */
    fun onRequestCaptured(request: HttpRequest) {}

    /**
     * Triggered when an HTTP response is captured by the proxy engine.
     */
    fun onResponseCaptured(
        transactionId: String,
        response: HttpResponse,
        durationMs: Long,
        timings: HttpTimings
    ) {}

    /**
     * Triggered when a complete request/response transaction finishes.
     */
    fun onTransactionCaptured(transaction: HttpTransaction) {}
}
