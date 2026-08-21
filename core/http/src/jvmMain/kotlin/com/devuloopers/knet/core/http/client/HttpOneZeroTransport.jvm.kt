package com.devuloopers.knet.core.http.client

import com.devuloopers.knet.domain.clientNetwork.model.OutboundRequestBody
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** JVM implementation used because Ktor CIO always serializes HTTP/1.1 request lines. */
internal actual suspend fun executeHttpOneZero(
    request: HttpOneZeroTransportRequest,
): HttpTransportResponse = runInterruptible {
    HttpOneZeroSocketTransport(request).execute()
}

/** One-shot socket transport; HTTP/1.0 defaults to connection-close so no pool ownership is needed. */
private class HttpOneZeroSocketTransport(
    private val initialRequest: HttpOneZeroTransportRequest,
) {
    fun execute(): HttpTransportResponse {
        var currentRequest = initialRequest
        var redirectCount = 0
        while (true) {
            val response = executeWithRetries(currentRequest)
            val location = response.headers.firstOrNull { (name, _) ->
                name.equals("Location", ignoreCase = true)
            }?.second
            if (!currentRequest.configuration.followRedirects ||
                response.statusCode !in REDIRECT_STATUS_CODES ||
                location.isNullOrBlank()
            ) {
                return response
            }
            check(++redirectCount <= MAX_REDIRECTS) { "Too many HTTP redirects." }
            currentRequest = currentRequest.redirectedTo(location, response.statusCode)
        }
    }

    private fun executeWithRetries(request: HttpOneZeroTransportRequest): HttpTransportResponse {
        var lastFailure: Exception? = null
        repeat(request.configuration.retryCount.coerceAtLeast(0) + 1) {
            try {
                return executeOnce(request)
            } catch (failure: Exception) {
                lastFailure = failure
            }
        }
        throw checkNotNull(lastFailure)
    }

    private fun executeOnce(request: HttpOneZeroTransportRequest): HttpTransportResponse {
        val uri = request.url.toHttpUri()
        val target = TargetEndpoint.from(uri)
        val routeThroughProxy = request.proxyPort?.let { it > 0 } == true
        val plainSocket = openSocket(
            host = if (routeThroughProxy) LOOPBACK_HOST else target.host,
            port = if (routeThroughProxy) checkNotNull(request.proxyPort) else target.port,
            request = request,
        )

        return plainSocket.use { connectedSocket ->
            val transportSocket = if (target.secure) {
                if (routeThroughProxy) establishProxyTunnel(connectedSocket, target)
                openTlsSocket(
                    socket = connectedSocket,
                    target = target,
                    request = request,
                )
            } else {
                connectedSocket
            }

            val closeTransportSeparately = transportSocket !== connectedSocket
            try {
                val requestTarget = if (routeThroughProxy && !target.secure) {
                    uri.withoutFragment().toASCIIString()
                } else {
                    uri.originForm()
                }
                val encodedBody = request.body.encodeForTransport()
                writeRequest(
                    output = transportSocket.getOutputStream(),
                    request = request,
                    target = target,
                    requestTarget = requestTarget,
                    encodedBody = encodedBody,
                )
                readResponse(transportSocket.getInputStream(), request.method.token)
            } finally {
                if (closeTransportSeparately) transportSocket.close()
            }
        }
    }

    private fun openSocket(host: String, port: Int, request: HttpOneZeroTransportRequest): Socket =
        Socket().apply {
            connect(
                InetSocketAddress(host, port),
                request.configuration.connectTimeoutMillis.toSocketTimeout(),
            )
            soTimeout = request.configuration.timeoutMillis.toSocketTimeout()
            tcpNoDelay = true
        }

    private fun establishProxyTunnel(socket: Socket, target: TargetEndpoint) {
        val authority = target.authority
        socket.getOutputStream().apply {
            write(
                buildString {
                    append("CONNECT ").append(authority).append(" HTTP/1.0\r\n")
                    append("Host: ").append(authority).append("\r\n")
                    append("Proxy-Connection: keep-alive\r\n\r\n")
                }.toByteArray(Charsets.ISO_8859_1),
            )
            flush()
        }
        val responseHead = readResponseHead(socket.getInputStream())
        check(responseHead.statusCode in 200..299) {
            "Proxy CONNECT failed with ${responseHead.statusCode} ${responseHead.reasonPhrase}".trim()
        }
    }

    private fun openTlsSocket(
        socket: Socket,
        target: TargetEndpoint,
        request: HttpOneZeroTransportRequest,
    ): SSLSocket {
        val trustManager: X509TrustManager = PlatformHttpTrustManager.get(
            verifySsl = request.configuration.verifySsl,
            localProxyTlsTrust = request.localProxyTlsTrust.takeIf { request.proxyPort != null },
        )
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        return (context.socketFactory.createSocket(socket, target.host, target.port, true) as SSLSocket).apply {
            if (request.configuration.verifySsl) {
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            }
            startHandshake()
        }
    }

    private fun writeRequest(
        output: OutputStream,
        request: HttpOneZeroTransportRequest,
        target: TargetEndpoint,
        requestTarget: String,
        encodedBody: EncodedTransportBody,
    ) {
        val headers = LinkedHashMap<String, String>()
        request.headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank() && name.isSafeHeaderName() && value.isSafeHeaderValue()) {
                headers[name] = value
            }
        }
        headers.removeCaseInsensitive("Host")
        headers.removeCaseInsensitive("Content-Length")
        headers.removeCaseInsensitive("Transfer-Encoding")
        headers.removeCaseInsensitive("Connection")
        headers.removeCaseInsensitive("Proxy-Connection")
        headers["Host"] = target.authority
        headers["Connection"] = "close"
        encodedBody.contentType?.takeIf { headers.keyCaseInsensitive("Content-Type") == null }?.let { contentType ->
            headers["Content-Type"] = contentType
        }
        if (encodedBody.bytes.isNotEmpty()) headers["Content-Length"] = encodedBody.bytes.size.toString()

        val head = buildString {
            append(request.method.token).append(' ').append(requestTarget).append(" HTTP/1.0\r\n")
            headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        if (encodedBody.bytes.isNotEmpty()) output.write(encodedBody.bytes)
        output.flush()
    }

    private fun readResponse(input: InputStream, requestMethod: String): HttpTransportResponse {
        var head = readResponseHead(input)
        while (head.statusCode in 100..199 && head.statusCode != 101) {
            head = readResponseHead(input)
        }
        val hasBody = !requestMethod.equals("HEAD", ignoreCase = true) &&
            head.statusCode !in 100..199 && head.statusCode != 204 && head.statusCode != 304
        val body = when {
            !hasBody -> byteArrayOf()
            head.headers.hasChunkedTransferEncoding() -> input.readChunkedBody()
            else -> head.headers.contentLength()?.let(input::readExactly) ?: input.readToEndBounded()
        }
        return HttpTransportResponse(
            statusCode = head.statusCode,
            reasonPhrase = head.reasonPhrase,
            protocol = ApplicationProtocol.fromToken(head.protocolToken),
            headers = head.headers,
            body = body,
        )
    }
}

private data class TargetEndpoint(
    val host: String,
    val port: Int,
    val secure: Boolean,
) {
    val authority: String
        get() {
            val renderedHost = if (':' in host && !host.startsWith('[')) "[$host]" else host
            val defaultPort = if (secure) 443 else 80
            return if (port == defaultPort) renderedHost else "$renderedHost:$port"
        }

    companion object {
        fun from(uri: URI): TargetEndpoint {
            val secure = when (uri.scheme.lowercase()) {
                "http" -> false
                "https" -> true
                else -> throw IllegalArgumentException("URL scheme must be HTTP or HTTPS.")
            }
            val host = uri.host?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("URL must contain a valid host.")
            return TargetEndpoint(
                host = host,
                port = uri.port.takeIf { it > 0 } ?: if (secure) 443 else 80,
                secure = secure,
            )
        }
    }
}

private data class ResponseHead(
    val protocolToken: String,
    val statusCode: Int,
    val reasonPhrase: String,
    val headers: List<Pair<String, String>>,
)

private fun readResponseHead(input: InputStream): ResponseHead {
    val statusLine = input.readAsciiLine()
    val parts = statusLine.split(' ', limit = 3)
    require(parts.size >= 2 && parts[0].startsWith("HTTP/")) { "Invalid HTTP response status line." }
    val headers = mutableListOf<Pair<String, String>>()
    var headerBytes = statusLine.length
    while (true) {
        val line = input.readAsciiLine()
        headerBytes += line.length
        require(headerBytes <= MAX_HEADER_BYTES) { "HTTP response headers exceed $MAX_HEADER_BYTES bytes." }
        if (line.isEmpty()) break
        val separator = line.indexOf(':')
        require(separator > 0) { "Invalid HTTP response header." }
        headers += line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        require(headers.size <= MAX_HEADER_COUNT) { "HTTP response contains too many headers." }
    }
    return ResponseHead(
        protocolToken = parts[0],
        statusCode = parts[1].toInt(),
        reasonPhrase = parts.getOrElse(2) { "" },
        headers = headers,
    )
}

private fun InputStream.readAsciiLine(): String {
    val output = ByteArrayOutputStream()
    var previousWasCarriageReturn = false
    while (true) {
        val next = read()
        if (next < 0) throw EOFException("Connection closed before the HTTP head completed.")
        if (previousWasCarriageReturn && next == '\n'.code) break
        if (previousWasCarriageReturn) output.write('\r'.code)
        previousWasCarriageReturn = next == '\r'.code
        if (!previousWasCarriageReturn) output.write(next)
        require(output.size() <= MAX_LINE_BYTES) { "HTTP response line exceeds $MAX_LINE_BYTES bytes." }
    }
    return output.toString(Charsets.ISO_8859_1)
}

private fun InputStream.readExactly(byteCount: Long): ByteArray {
    require(byteCount in 0..MAX_BODY_BYTES) { "HTTP response body is too large for API Studio." }
    val output = ByteArrayOutputStream(byteCount.toInt())
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = byteCount
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) throw EOFException("Connection closed before the response body completed.")
        if (read == 0) continue
        output.write(buffer, 0, read)
        remaining -= read
    }
    return output.toByteArray()
}

private fun InputStream.readChunkedBody(): ByteArray {
    val output = ByteArrayOutputStream()
    while (true) {
        val size = readAsciiLine().substringBefore(';').trim().toLong(16)
        require(size in 0..MAX_BODY_BYTES) { "Invalid or oversized HTTP chunk." }
        if (size == 0L) {
            while (readAsciiLine().isNotEmpty()) {
                // Consume optional trailer fields through their terminating empty line.
            }
            return output.toByteArray()
        }
        require(output.size().toLong() + size <= MAX_BODY_BYTES) {
            "HTTP response body is too large for API Studio."
        }
        output.write(readExactly(size))
        require(readAsciiLine().isEmpty()) { "Invalid HTTP chunk terminator." }
    }
}

private fun InputStream.readToEndBounded(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        require(output.size().toLong() + read <= MAX_BODY_BYTES) {
            "HTTP response body is too large for API Studio."
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun List<Pair<String, String>>.hasChunkedTransferEncoding(): Boolean = any { (name, value) ->
    name.equals("Transfer-Encoding", ignoreCase = true) &&
        value.split(',').any { token -> token.trim().equals("chunked", ignoreCase = true) }
}

private fun List<Pair<String, String>>.contentLength(): Long? = firstOrNull { (name, _) ->
    name.equals("Content-Length", ignoreCase = true)
}?.second?.trim()?.toLongOrNull()

private fun HttpOneZeroTransportRequest.redirectedTo(
    location: String,
    statusCode: Int,
): HttpOneZeroTransportRequest {
    val redirectedUrl = url.toHttpUri().resolve(location).toString()
    val switchToGet = statusCode == 303 ||
        (statusCode in 301..302 && method.token.equals("POST", ignoreCase = true))
    return copy(
        url = redirectedUrl,
        method = if (switchToGet) com.devuloopers.knet.traffic.model.http.HttpMethod.GET else method,
        body = if (switchToGet) OutboundRequestBody.None else body,
        headers = if (switchToGet) headers.filterKeys { name ->
            !name.equals("Content-Type", ignoreCase = true)
        } else headers,
    )
}

private fun URI.originForm(): String = buildString {
    append(rawPath?.takeIf(String::isNotEmpty) ?: "/")
    rawQuery?.let { append('?').append(it) }
}

private fun URI.withoutFragment(): URI = URI(toASCIIString().substringBefore('#'))

private fun String.toHttpUri(): URI = try {
    URI(this).also { uri -> require(uri.isAbsolute) { "URL must be absolute." } }
} catch (failure: Exception) {
    throw IllegalArgumentException("Invalid HTTP URL.", failure)
}

private fun String.isSafeHeaderName(): Boolean = none { it == ':' || it == '\r' || it == '\n' }
private fun String.isSafeHeaderValue(): Boolean = '\r' !in this && '\n' !in this
private fun MutableMap<String, String>.removeCaseInsensitive(name: String) {
    keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
}
private fun Map<String, String>.keyCaseInsensitive(name: String): String? =
    keys.firstOrNull { it.equals(name, ignoreCase = true) }
private fun Long.toSocketTimeout(): Int = coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
private const val LOOPBACK_HOST = "127.0.0.1"
private const val MAX_REDIRECTS = 10
private const val MAX_LINE_BYTES = 64 * 1024
private const val MAX_HEADER_BYTES = 256 * 1024
private const val MAX_HEADER_COUNT = 1_024
private const val MAX_BODY_BYTES = 256L * 1024L * 1024L
