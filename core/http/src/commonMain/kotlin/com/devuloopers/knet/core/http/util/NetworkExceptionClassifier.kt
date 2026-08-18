package com.devuloopers.knet.core.http.util

import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.RedirectResponseException
import kotlinx.coroutines.CancellationException

/**
 * Utility inspector that maps caught runtime execution exceptions to strongly-typed [NetworkFailureReason] variants.
 */
object NetworkExceptionClassifier {

    /**
     * Inspects a caught execution exception and extracts host/url metadata to classify
     * the underlying network failure reason.
     *
     * @param exception Caught execution exception.
     * @param targetUrl Target HTTP request URL.
     * @param timeoutMs Configured request timeout in milliseconds.
     * @return Strongly-typed [NetworkFailureReason] variant.
     */
    fun classify(
        exception: Throwable,
        targetUrl: String = "",
        timeoutMs: Long = 0L
    ): NetworkFailureReason {
        if (exception is CancellationException) {
            return NetworkFailureReason.Cancelled
        }

        // Priority 1: Check URL scheme format if targetUrl is specified
        if (targetUrl.isNotBlank() && !targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            return NetworkFailureReason.InvalidUrl(
                url = targetUrl,
                detail = "URL must start with a valid scheme ('http://' or 'https://')."
            )
        }

        val extractedHost = extractHostFromUrl(targetUrl)
        var current: Throwable? = exception

        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            val className = current::class.simpleName?.lowercase().orEmpty()
            val platformFailure = current.platformNetworkFailure()

            when {
                // 1. Invalid URL / Malformed scheme
                current is IllegalArgumentException && (message.contains("url") || message.contains("scheme") || message.contains("port")) -> {
                    return NetworkFailureReason.InvalidUrl(url = targetUrl, detail = current.message ?: "Invalid URL format")
                }

                // 2. DNS Host Resolution Failure (including NIO UnresolvedAddressException)
                platformFailure == PlatformNetworkFailure.DNS ||
                        className.contains("unresolved") || className.contains("unknownhost") ||
                        message.contains("no such host") || message.contains("host not found") || message.contains("name or service not known") -> {
                    val hostName = extractedHost.ifBlank { targetUrl }
                    return NetworkFailureReason.HostNotFound(
                        host = hostName,
                        detail = "The domain name '$hostName' could not be resolved by DNS."
                    )
                }

                // 3. Timeout
                current is HttpRequestTimeoutException || platformFailure == PlatformNetworkFailure.TIMEOUT || className.contains("timeout") ||
                        message.contains("timed out") || message.contains("timeout") -> {
                    return NetworkFailureReason.Timeout(
                        timeoutMs = timeoutMs,
                        detail = if (timeoutMs > 0) "The server did not respond within ${timeoutMs}ms." else "Request execution timed out."
                    )
                }

                // 4. SSL / TLS Handshake Failure
                platformFailure == PlatformNetworkFailure.TLS || className.contains("ssl") || className.contains("cert") ||
                        message.contains("ssl") || message.contains("certificate") -> {
                    return NetworkFailureReason.SslHandshakeFailed(
                        detail = current.message?.takeIf { it.isNotBlank() } ?: "Failed to establish a secure SSL/TLS connection."
                    )
                }

                // 5. Proxy Failure
                message.contains("proxy") || className.contains("proxy") -> {
                    return NetworkFailureReason.ProxyFailure(
                        proxyPort = null,
                        detail = current.message?.takeIf { it.isNotBlank() } ?: "Local proxy server failed to forward request."
                    )
                }

                // 6. Too Many Redirects
                current is RedirectResponseException || message.contains("redirect") -> {
                    return NetworkFailureReason.TooManyRedirects(
                        detail = current.message?.takeIf { it.isNotBlank() } ?: "Exceeded maximum HTTP redirect limit."
                    )
                }

                // 7. Network Offline / Connection Refused / Server Unreachable
                platformFailure == PlatformNetworkFailure.UNREACHABLE || className.contains("connect") || className.contains("socket") ||
                        message.contains("connection refused") || message.contains("unreachable") || message.contains("connection reset") -> {
                    return NetworkFailureReason.OfflineOrUnreachable(
                        detail = current.message?.takeIf { it.isNotBlank() } ?: "Could not establish connection to target host/port."
                    )
                }
            }
            current = current.cause
        }

        // Clean raw Java exception string for generic fallback
        val rawMessage = exception.message ?: exception.toString()
        val cleanMessage = formatHumanReadableErrorMessage(rawMessage, extractedHost)
        return NetworkFailureReason.Generic(message = cleanMessage)
    }

    /**
     * Formats technical Java exception strings into clean, user-friendly natural language.
     */
    private fun formatHumanReadableErrorMessage(rawMessage: String, host: String): String {
        return when {
            rawMessage.contains("UnresolvedAddressException") -> {
                "Unable to resolve the web address for '$host'. Please check that the URL domain is correct and your internet connection is active."
            }
            rawMessage.contains("ConnectException") || rawMessage.contains("Connection refused") -> {
                "Connection refused by '$host'. The server may be offline or not accepting connections."
            }
            rawMessage.contains("SocketTimeoutException") || rawMessage.contains("Timeout") -> {
                "The connection to '$host' timed out before completing."
            }
            rawMessage.contains("SSLException") || rawMessage.contains("Certificate") -> {
                "SSL security check failed for '$host'. The server certificate could not be verified."
            }
            else -> {
                // Strip Java package prefixes e.g. "java.nio.channels.UnresolvedAddressException" -> "Unresolved Address Exception"
                rawMessage
                    .replace(Regex("([a-z0-9]+\\.)+([A-Z][a-zA-Z0-9]+)"), "$2")
                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            }
        }
    }

    private fun extractHostFromUrl(url: String): String {
        if (url.isBlank()) return ""
        return try {
            val clean = url.substringAfter("://").substringBefore("/").substringBefore("?")
            clean.substringBefore(":")
        } catch (_: Exception) {
            ""
        }
    }
}
