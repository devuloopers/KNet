package com.devuloopers.knet.domain.clientNetwork.model

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
     * Triggered when an in-flight transaction is dropped or cancelled.
     */
    fun onTransactionDropped(transactionId: String, reason: String = "Dropped") {}

    /**
     * Triggered when a complete request/response transaction finishes.
     */
    fun onTransactionCaptured(transaction: HttpTransaction) {}
}
