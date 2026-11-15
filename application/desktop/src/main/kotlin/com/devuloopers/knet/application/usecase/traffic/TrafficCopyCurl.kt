package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import java.net.URI

/** Produces a POSIX shell command from complete captured wire bytes; it never invokes a shell or network. */
internal object TrafficCopyCurl {
    suspend fun render(exchange: HttpExchangeSnapshot, content: TrafficCopyContent): String {
        val request = exchange.request
        val url = request.absoluteUrl()
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri?.scheme?.lowercase() !in setOf("http", "https") || uri?.host == null || request.head.method.token.equals("CONNECT", true))
            return "[cURL unavailable: an absolute HTTP or HTTPS request is required]"
        if (exchange.response?.head?.status?.code == 101 || request.head.headers.any {
                it.name.value.equals("upgrade", true) || it.name.value.equals(":protocol", true) ||
                it.name.value.equals("content-type", true) && it.value.startsWith("application/grpc", true)
            }) return "[cURL unavailable: use the protocol-specific message details for this connection]"
        if (request.trailers.isNotEmpty()) return "[cURL unavailable: request trailers cannot be represented faithfully]"
        if (!exchange.state.isTerminal && exchange.state in setOf(ExchangeState.REQUEST_HEADERS, ExchangeState.REQUEST_STREAMING))
            return "[cURL unavailable: request body is not finalized]"
        val payload = content.raw(request.body)
        if (!payload.complete || payload.bytes == null) return "[cURL unavailable: complete request bytes are required; use Export]"
        val namedHopHeaders = request.head.headers.filter { it.name.value.equals("connection", true) }.flatMap { it.value.split(',') }.map { it.trim().lowercase() }
        val headers = request.head.headers.filterNot { it.name.value.lowercase() in HOP_HEADERS || it.name.value.lowercase() in namedHopHeaders || it.name.value.startsWith(':') }
        if (headers.any { it.name.value.any(::invalid) || it.value.any(::invalid) } || url.any(::invalid) || request.head.method.token.any(::invalid))
            return "[cURL unavailable: captured command fields contain unsupported control characters]"
        val command = buildString {
            append("curl --globoff --path-as-is --request "); append(quote(request.head.method.token)); append(" --url "); append(quote(url))
            for (header in headers) { append(" \\\n  --header "); append(quote(if (header.value.isEmpty()) "${header.name.value};" else "${header.name.value}: ${header.value}")) }
            for (name in listOf("User-Agent", "Accept")) if (headers.none { it.name.value.equals(name, true) }) {
                append(" --header "); append(quote("$name:"))
            }
            if ((payload.bytes.isNotEmpty() || request.body is MessageBodyRef.Available) && headers.none { it.name.value.equals("Content-Type", true) })
                append(" --header 'Content-Type:'")
            if (payload.bytes.isNotEmpty()) append(" \\\n  --data-binary @-")
            else if (request.body is MessageBodyRef.Available) append(" --data-binary ''")
        }
        if (payload.bytes.isEmpty()) return command
        // Octal printf preserves NUL, invalid UTF-8 and compressed request bytes without a temporary file.
        val octal = payload.bytes.joinToString("") { "\\0" + it.toUByte().toString(8).padStart(3, '0') }
        return "printf '%b' ${quote(octal)} | $command"
    }
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    private fun invalid(value: Char): Boolean = value.code < 32 || value.code == 127
    private val HOP_HEADERS = setOf("content-length", "transfer-encoding", "connection", "proxy-connection", "keep-alive", "trailer", "upgrade", "proxy-authorization")
}
