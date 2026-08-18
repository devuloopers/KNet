package com.devuloopers.knet.domain.clientNetwork.model

/**
 * Centralized constant definitions for network socket connection and request execution timeouts.
 * Shared across Netty Proxy Engine, Ktor HTTP Client, and UI layers.
 */
object NetworkTimeouts {
    /** Default connection and request execution timeout in milliseconds (10,000 ms = 10 seconds). */
    const val DEFAULT_TIMEOUT_MS: Long = 10_000L

    /** Default channel option connection timeout in milliseconds (10,000 ms = 10 seconds). */
    const val DEFAULT_TIMEOUT_INT_MS: Int = 10_000
}
