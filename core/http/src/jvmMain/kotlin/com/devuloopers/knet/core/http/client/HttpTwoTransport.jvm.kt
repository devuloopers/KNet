package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/** JVM HTTP/2 adapter backed by reusable JDK ALPN clients and scoped KNet proxy trust. */
internal actual class HttpTwoTransport actual constructor() {
    private val lock = Any()
    private val clients = LinkedHashMap<ClientKey, HttpClient>(MAXIMUM_CACHED_CLIENTS, 0.75f, true)
    private val activeRequests = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    private val closed = AtomicBoolean(false)

    actual suspend fun execute(request: HttpTwoTransportRequest): HttpTransportResponse {
        check(!closed.get()) { "HTTP/2 transport is closed." }
        var lastFailure: Exception? = null
        repeat(request.configuration.retryCount.coerceAtLeast(0) + 1) {
            currentCoroutineContext().ensureActive()
            try {
                return executeOnce(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                lastFailure = failure
            }
        }
        throw checkNotNull(lastFailure)
    }

    actual suspend fun executeStreaming(
        request: HttpTwoTransportRequest,
        onResponseHead: suspend (HttpTransportResponseHead) -> Unit,
        onBodyChunk: suspend (ByteArray) -> Unit,
    ): HttpTransportResponse {
        check(!closed.get()) { "HTTP/2 transport is closed." }
        var lastFailure: Exception? = null
        repeat(request.configuration.retryCount.coerceAtLeast(0) + 1) {
            currentCoroutineContext().ensureActive()
            var responseStarted = false
            try {
                return executeStreamingOnce(
                    request = request,
                    onResponseHead = { head ->
                        responseStarted = true
                        onResponseHead(head)
                    },
                    onBodyChunk = onBodyChunk,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (responseStarted) throw failure
                lastFailure = failure
            }
        }
        throw checkNotNull(lastFailure)
    }

    private suspend fun executeOnce(request: HttpTwoTransportRequest): HttpTransportResponse {
        val encodedBody = request.body.encodeForTransport()
        val builder = HttpRequest.newBuilder(URI(request.url))
            .timeout(request.configuration.timeoutMillis.coerceAtLeast(1L).milliseconds.toJavaDuration())
            .method(
                request.method.token,
                if (encodedBody.bytes.isEmpty()) {
                    HttpRequest.BodyPublishers.noBody()
                } else {
                    HttpRequest.BodyPublishers.ofByteArray(encodedBody.bytes)
                },
            )
        request.headers.forEach { (name, value) ->
            if (name.isSafeHttpTwoHeaderName() && value.isSafeHeaderValue() && value.isNotBlank()) {
                builder.header(name, value)
            }
        }
        if (encodedBody.contentType != null && request.headers.keys.none { it.equals("content-type", true) }) {
            builder.header("Content-Type", encodedBody.contentType)
        }

        val response = clientFor(request).sendCancellable(
            builder.build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        check(!request.requireHttpTwo || response.version() == HttpClient.Version.HTTP_2) {
            "Exact HTTP/2 was requested, but the peer negotiated ${response.version().displayName()}."
        }
        return HttpTransportResponse(
            statusCode = response.statusCode(),
            reasonPhrase = "",
            protocol = ApplicationProtocol.fromToken(response.version().displayName()),
            headers = response.headers().map().flatMap { (name, values) -> values.map { value -> name to value } },
            body = response.body(),
        )
    }

    private suspend fun executeStreamingOnce(
        request: HttpTwoTransportRequest,
        onResponseHead: suspend (HttpTransportResponseHead) -> Unit,
        onBodyChunk: suspend (ByteArray) -> Unit,
    ): HttpTransportResponse {
        val encodedBody = request.body.encodeForTransport()
        val builder = request.toJdkRequestBuilder(encodedBody)
        val response = clientFor(request).sendCancellable(
            builder.build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        check(!request.requireHttpTwo || response.version() == HttpClient.Version.HTTP_2) {
            "Exact HTTP/2 was requested, but the peer negotiated ${response.version().displayName()}."
        }
        val headers = response.headers().map().flatMap { (name, values) -> values.map { value -> name to value } }
        val head = HttpTransportResponseHead(
            statusCode = response.statusCode(),
            reasonPhrase = "",
            protocol = ApplicationProtocol.fromToken(response.version().displayName()),
            headers = headers,
        )
        onResponseHead(head)
        val retainTerminalBody = !headers.isIdentityEventStreamHeaders()
        val retained = ByteArrayOutputStream()
        response.body().use { input ->
            val buffer = ByteArray(STREAM_CHUNK_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = runInterruptible { input.read(buffer) }
                if (read < 0) break
                if (read == 0) continue
                val chunk = buffer.copyOf(read)
                onBodyChunk(chunk)
                if (retainTerminalBody) {
                    check(retained.size() <= MAXIMUM_TERMINAL_BODY_BYTES - read) {
                        "HTTP response exceeds the bounded API Studio body limit."
                    }
                    retained.write(chunk)
                }
            }
        }
        return HttpTransportResponse(
            statusCode = head.statusCode,
            reasonPhrase = head.reasonPhrase,
            protocol = head.protocol,
            headers = head.headers,
            body = retained.toByteArray(),
        )
    }

    private fun HttpTwoTransportRequest.toJdkRequestBuilder(
        encodedBody: EncodedTransportBody,
    ): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI(url))
            .timeout(configuration.timeoutMillis.coerceAtLeast(1L).milliseconds.toJavaDuration())
            .method(
                method.token,
                if (encodedBody.bytes.isEmpty()) {
                    HttpRequest.BodyPublishers.noBody()
                } else {
                    HttpRequest.BodyPublishers.ofByteArray(encodedBody.bytes)
                },
            )
        headers.forEach { (name, value) ->
            if (name.isSafeHttpTwoHeaderName() && value.isSafeHeaderValue() && value.isNotBlank()) {
                builder.header(name, value)
            }
        }
        if (encodedBody.contentType != null && headers.keys.none { it.equals("content-type", true) }) {
            builder.header("Content-Type", encodedBody.contentType)
        }
        return builder
    }

    /** Reuses multiplex-capable clients while bounding configuration/trust variants retained by one API client. */
    private fun clientFor(request: HttpTwoTransportRequest): HttpClient {
        val key = ClientKey.from(request)
        synchronized(lock) {
            clients[key]?.let { return it }
            check(!closed.get()) { "HTTP/2 transport is closed." }
            return createClient(request).also { client ->
                clients[key] = client
                while (clients.size > MAXIMUM_CACHED_CLIENTS) {
                    val eldest = clients.entries.iterator()
                    if (eldest.hasNext()) {
                        eldest.next()
                        eldest.remove()
                    }
                }
            }
        }
    }

    private fun createClient(request: HttpTwoTransportRequest): HttpClient {
        val trustManager = PlatformHttpTrustManager.get(
            verifySsl = request.configuration.verifySsl,
            localProxyTlsTrust = request.localProxyTlsTrust.takeIf { request.proxyPort != null },
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        val clientBuilder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(sslContext)
            .connectTimeout(
                request.configuration.connectTimeoutMillis.coerceAtLeast(1L).milliseconds.toJavaDuration(),
            )
            .followRedirects(
                if (request.configuration.followRedirects) {
                    HttpClient.Redirect.NORMAL
                } else {
                    HttpClient.Redirect.NEVER
                },
            )
        request.proxyPort?.takeIf { it > 0 }?.let { port ->
            clientBuilder.proxy(ProxySelector.of(InetSocketAddress(LOOPBACK_HOST, port)))
        }
        return clientBuilder.build()
    }

    private suspend fun <T> HttpClient.sendCancellable(
        request: HttpRequest,
        bodyHandler: HttpResponse.BodyHandler<T>,
    ): HttpResponse<T> = suspendCancellableCoroutine { continuation ->
        val future = sendAsync(request, bodyHandler)
        activeRequests += future
        if (closed.get()) future.cancel(true)
        continuation.invokeOnCancellation { future.cancel(true) }
        future.whenComplete { response, failure ->
            activeRequests -= future
            when {
                continuation.isCancelled -> Unit
                failure != null -> continuation.resumeWithException(failure.unwrapCompletionFailure())
                else -> continuation.resume(response)
            }
        }
    }

    actual fun close() {
        if (!closed.compareAndSet(false, true)) return
        activeRequests.toList().forEach { future -> future.cancel(true) }
        synchronized(lock) {
            clients.clear()
        }
    }

    private data class ClientKey(
        val proxyPort: Int?,
        val verifySsl: Boolean,
        val connectTimeoutMillis: Long,
        val followRedirects: Boolean,
        val localProxyCertificateSha256: String?,
    ) {
        companion object {
            fun from(request: HttpTwoTransportRequest): ClientKey = ClientKey(
                proxyPort = request.proxyPort?.takeIf { it > 0 },
                verifySsl = request.configuration.verifySsl,
                connectTimeoutMillis = request.configuration.connectTimeoutMillis.coerceAtLeast(1L),
                followRedirects = request.configuration.followRedirects,
                localProxyCertificateSha256 = request.localProxyTlsTrust
                    ?.takeIf { request.proxyPort != null }
                    ?.certificateAuthorityDerCopy()
                    ?.let(::sha256),
            )
        }
    }

    private companion object {
        const val MAXIMUM_CACHED_CLIENTS: Int = 8
        const val STREAM_CHUNK_BYTES: Int = 8 * 1_024
        const val MAXIMUM_TERMINAL_BODY_BYTES: Int = 16 * 1_024 * 1_024
    }
}

private fun List<Pair<String, String>>.isIdentityEventStreamHeaders(): Boolean {
    val eventStream = any { (name, value) ->
        name.equals("content-type", ignoreCase = true) &&
            value.substringBefore(';').trim().equals("text/event-stream", ignoreCase = true)
    }
    val contentEncoding = firstOrNull { (name, _) ->
        name.equals("content-encoding", ignoreCase = true)
    }?.second?.trim()?.lowercase()
    return eventStream && (contentEncoding.isNullOrEmpty() || contentEncoding == "identity")
}

private fun Throwable.unwrapCompletionFailure(): Throwable = cause ?: this

private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(value)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.isSafeHttpTwoHeaderName(): Boolean = isNotBlank() &&
    lowercase() !in HTTP_TWO_MANAGED_HEADERS &&
    none { character -> character == ':' || character == '\r' || character == '\n' }

private fun String.isSafeHeaderValue(): Boolean = '\r' !in this && '\n' !in this

private fun HttpClient.Version.displayName(): String = when (this) {
    HttpClient.Version.HTTP_1_1 -> "HTTP/1.1"
    HttpClient.Version.HTTP_2 -> "HTTP/2"
}

private val HTTP_TWO_MANAGED_HEADERS = setOf(
    "connection",
    "content-length",
    "expect",
    "host",
    "http2-settings",
    "proxy-connection",
    "transfer-encoding",
    "upgrade",
)

private const val LOOPBACK_HOST = "127.0.0.1"
