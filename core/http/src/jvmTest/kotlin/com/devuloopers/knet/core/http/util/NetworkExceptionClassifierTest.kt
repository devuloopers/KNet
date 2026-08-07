package com.devuloopers.knet.core.http.util

import com.devuloopers.knet.domain.clientNetwork.model.NetworkFailureReason
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end unit tests verifying priority-based classification of execution exceptions into strongly-typed [NetworkFailureReason]s.
 */
class NetworkExceptionClassifierTest {

    @Test
    fun `classify returns Cancelled for CancellationException`() {
        val result = NetworkExceptionClassifier.classify(CancellationException("Task cancelled"))
        assertEquals(NetworkFailureReason.Cancelled, result)
    }

    @Test
    fun `classify returns InvalidUrl when URL lacks http or https scheme`() {
        val result = NetworkExceptionClassifier.classify(
            exception = IllegalArgumentException("Invalid URL"),
            targetUrl = "api.example.com/v1/users"
        )
        assertTrue(result is NetworkFailureReason.InvalidUrl)
        assertEquals("api.example.com/v1/users", result.url)
    }

    @Test
    fun `classify returns HostNotFound for UnresolvedAddressException`() {
        val result = NetworkExceptionClassifier.classify(
            exception = UnresolvedAddressException(),
            targetUrl = "https://api.example.com/v1/users"
        )
        assertTrue(result is NetworkFailureReason.HostNotFound)
        assertEquals("api.example.com", result.host)
        assertTrue(result.detail.contains("could not be resolved"))
    }

    @Test
    fun `classify returns HostNotFound for UnknownHostException`() {
        val result = NetworkExceptionClassifier.classify(
            exception = UnknownHostException("api.example.com: no such host"),
            targetUrl = "https://api.example.com/v1/users"
        )
        assertTrue(result is NetworkFailureReason.HostNotFound)
        assertEquals("api.example.com", result.host)
    }

    @Test
    fun `classify returns Timeout for SocketTimeoutException`() {
        val result = NetworkExceptionClassifier.classify(
            exception = SocketTimeoutException("Read timed out"),
            targetUrl = "https://api.example.com/v1/users",
            timeoutMs = 5000L
        )
        assertTrue(result is NetworkFailureReason.Timeout)
        assertEquals(5000L, result.timeoutMs)
    }

    @Test
    fun `classify returns OfflineOrUnreachable for ConnectException`() {
        val result = NetworkExceptionClassifier.classify(
            exception = ConnectException("Connection refused"),
            targetUrl = "https://localhost:8080/api"
        )
        assertTrue(result is NetworkFailureReason.OfflineOrUnreachable)
    }

    @Test
    fun `classify returns SslHandshakeFailed for SSLHandshakeException`() {
        val result = NetworkExceptionClassifier.classify(
            exception = SSLHandshakeException("PKIX path building failed"),
            targetUrl = "https://self-signed.badssl.com"
        )
        assertTrue(result is NetworkFailureReason.SslHandshakeFailed)
    }

    @Test
    fun `classify returns Generic with clean message for uncategorized exception`() {
        val result = NetworkExceptionClassifier.classify(
            exception = IllegalStateException("Unexpected pipeline state"),
            targetUrl = "https://api.example.com"
        )
        assertTrue(result is NetworkFailureReason.Generic)
    }
}
