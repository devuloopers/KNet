package com.devuloopers.knet.domain.clientNetwork.model

/**
 * Centralized constant definitions for custom KNet internal HTTP header tokens.
 * Used for cross-module transaction correlation and internal proxy communication.
 */
public object KNetHeaders {
    /**
     * Unique transaction identifier injected into outgoing HTTP client requests
     * to correlate real-time proxy attempt events with subsequent Netty proxy captures.
     */
    public const val HEADER_TRANSACTION_ID: String = "X-KNet-Transaction-Id"

    /**
     * Internal proxy error header attached to synthetic fallback response transactions
     * when a network or local DNS resolution failure occurs while proxy mode is active.
     */
    public const val HEADER_PROXY_ERROR: String = "X-KNet-Proxy-Error"
}
