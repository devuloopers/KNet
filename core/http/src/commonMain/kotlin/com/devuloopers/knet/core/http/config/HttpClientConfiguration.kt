package com.devuloopers.knet.core.http.config

/**
 * Data class encapsulating configuration properties for the HTTP client execution pipeline.
 *
 * @property timeoutMillis Maximum duration allowed for request execution in milliseconds.
 * @property connectTimeoutMillis Maximum duration allowed for TCP socket connection in milliseconds.
 * @property retryCount Number of retry attempts for failed HTTP requests.
 * @property followRedirects Whether the client automatically follows 3xx HTTP redirects.
 * @property verifySsl Whether SSL/TLS certificates are verified during HTTPS handshakes.
 * @property useCookies Whether HTTP session cookies are enabled and retained.
 */
data class HttpClientConfiguration(
    val timeoutMillis: Long = 30_000L,
    val connectTimeoutMillis: Long = 10_000L,
    val retryCount: Int = 3,
    val followRedirects: Boolean = true,
    val verifySsl: Boolean = true,
    val useCookies: Boolean = true
)
