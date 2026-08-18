package com.devuloopers.knet.domain.clientNetwork.model

/**
 * Strongly-typed domain sealed hierarchy representing all possible outbound HTTP request execution failure categories.
 *
 * Provides granular failure reasons to allow UI components to present tailored diagnostic guidance
 * (such as host resolution tips, timeout settings, or SSL troubleshooting) instead of generic error strings.
 */
sealed interface NetworkFailureReason {

    /**
     * Malformed URL, unsupported scheme, or invalid port string.
     *
     * @property url The raw input URL string that failed parsing.
     * @property detail Detailed description of the URL parsing error.
     */
    data class InvalidUrl(val url: String, val detail: String) : NetworkFailureReason

    /**
     * Domain name resolution failure (e.g. UnknownHostException for "api.example.com").
     *
     * @property host The target domain/hostname that failed DNS resolution.
     * @property detail Detailed exception message or system error description.
     */
    data class HostNotFound(val host: String, val detail: String) : NetworkFailureReason

    /**
     * Socket, connection, or request timeout elapsed before completion.
     *
     * @property timeoutMs Configured or elapsed timeout duration in milliseconds.
     * @property detail Detailed timeout error message.
     */
    data class Timeout(val timeoutMs: Long, val detail: String) : NetworkFailureReason

    /**
     * Network interface offline, target server port closed, or connection refused.
     *
     * @property detail Detailed network connection failure message.
     */
    data class OfflineOrUnreachable(val detail: String) : NetworkFailureReason

    /**
     * Local proxy server connection or proxy handshake failed.
     *
     * @property proxyPort Target local proxy port integer, or null if unspecified.
     * @property detail Detailed proxy failure message.
     */
    data class ProxyFailure(val proxyPort: Int?, val detail: String) : NetworkFailureReason

    /**
     * SSL/TLS certificate validation or handshake failure.
     *
     * @property detail Detailed SSL/TLS error message or certificate exception details.
     */
    data class SslHandshakeFailed(val detail: String) : NetworkFailureReason

    /**
     * Exceeded maximum redirect count or encountered infinite HTTP redirect loop.
     *
     * @property detail Detailed redirect failure message.
     */
    data class TooManyRedirects(val detail: String) : NetworkFailureReason

    /**
     * Request execution was explicitly aborted by user or coroutine scope cancellation.
     */
    data object Cancelled : NetworkFailureReason

    /**
     * Generic fallback for uncategorized execution exceptions.
     *
     * @property message Uncategorized error description.
     */
    data class Generic(val message: String) : NetworkFailureReason
}

/**
 * Returns a human-readable detail message string for any [NetworkFailureReason] variant.
 */
val NetworkFailureReason.detailMessage: String
    get() = when (this) {
        is NetworkFailureReason.InvalidUrl -> detail
        is NetworkFailureReason.HostNotFound -> detail
        is NetworkFailureReason.Timeout -> detail
        is NetworkFailureReason.OfflineOrUnreachable -> detail
        is NetworkFailureReason.ProxyFailure -> detail
        is NetworkFailureReason.SslHandshakeFailed -> detail
        is NetworkFailureReason.TooManyRedirects -> detail
        is NetworkFailureReason.Cancelled -> "Request execution was cancelled."
        is NetworkFailureReason.Generic -> message
    }
