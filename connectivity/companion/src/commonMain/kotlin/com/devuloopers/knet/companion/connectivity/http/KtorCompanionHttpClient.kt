package com.devuloopers.knet.companion.connectivity.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.content.ByteArrayContent
import io.ktor.utils.io.readAvailable
import com.devuloopers.knet.companion.model.CompanionEndpointScheme
import kotlin.coroutines.cancellation.CancellationException

/** Shared bounded HTTP exchange used by Android and iOS companion transports. */
internal class KtorCompanionHttpClient(
    private val clientProvider: CompanionKtorClientProvider,
) {
    suspend fun execute(request: CompanionHttpRequest): CompanionHttpResponse {
        val handle = clientProvider.create(request)
        val client = handle.client
        try {
            val requestHost = handle.requestHost ?: request.endpoint.host
            val response = client.request {
                method = request.method.toKtorMethod()
                url {
                    protocol = when (request.endpoint.scheme) {
                        CompanionEndpointScheme.HTTP -> URLProtocol.HTTP
                        CompanionEndpointScheme.HTTPS -> URLProtocol.HTTPS
                    }
                    host = requestHost
                    port = request.endpoint.port
                    encodedPathSegments = request.path.removePrefix("/").split('/')
                }
                request.tlsServerName
                    ?.takeUnless { serverName -> serverName == requestHost }
                    ?.let { serverName -> header(HttpHeaders.Host, serverName) }
                request.acceptedMediaType?.let { mediaType -> header(HttpHeaders.Accept, mediaType) }
                request.authorization?.let { value -> header(HttpHeaders.Authorization, value) }
                request.additionalHeaders.forEach { (name, value) -> header(name, value) }
                val body = request.copyBody()
                if (body.isNotEmpty() || request.method == CompanionHttpMethod.POST) {
                    val contentType = request.requestMediaType?.let(ContentType::parse)
                        ?: ContentType.Application.OctetStream
                    setBody(ByteArrayContent(body, contentType))
                }
            }
            val responseHeaders = response.headers.names().associate { name ->
                val values = requireNotNull(response.headers.getAll(name))
                require(values.size == 1) { "Companion HTTP response contains duplicate headers." }
                name.lowercase() to values.single()
            }
            val declaredLength = requireNotNull(responseHeaders[HttpHeaders.ContentLength.lowercase()]?.toLongOrNull()) {
                "Companion HTTP response must provide a valid content length."
            }
            require(declaredLength in 0..request.maximumResponseBytes.toLong()) {
                "Companion HTTP response body exceeds the configured limit."
            }
            val body = response.bodyAsChannel().readBounded(request.maximumResponseBytes)
            require(body.size.toLong() == declaredLength) { "Companion HTTP response body length is invalid." }
            return CompanionHttpResponse(response.status.value, responseHeaders, body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            handle.securityFailure(failure)?.let { securityFailure -> throw securityFailure }
            failure.findSecurityFailure()?.let { securityFailure -> throw securityFailure }
            throw failure
        } finally {
            handle.close()
        }
    }
}

private fun Throwable.findSecurityFailure(): CompanionHttpSecurityException? {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    while (current != null && visited.add(current)) {
        if (current is CompanionHttpSecurityException) return current
        current = current.cause
    }
    return null
}

/** Applies the invariant companion transport policy to an engine-specific client. */
internal fun HttpClientConfig<*>.configureCompanionClient() {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000L
        requestTimeoutMillis = 15_000L
        socketTimeoutMillis = 10_000L
    }
}

private fun CompanionHttpMethod.toKtorMethod(): HttpMethod = when (this) {
    CompanionHttpMethod.GET -> HttpMethod.Get
    CompanionHttpMethod.POST -> HttpMethod.Post
}

private suspend fun io.ktor.utils.io.ByteReadChannel.readBounded(maximumBytes: Int): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    var total = 0
    val buffer = ByteArray(8 * 1024)
    while (!isClosedForRead) {
        val read = readAvailable(buffer)
        if (read < 0) break
        if (read == 0) continue
        check(total <= maximumBytes - read) { "Companion HTTP response body exceeds the configured limit." }
        chunks += buffer.copyOf(read)
        total += read
    }
    return ByteArray(total).also { result ->
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
    }
}
