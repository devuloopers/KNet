package com.devuloopers.knet.engine.util

import com.devuloopers.knet.model.HttpRequest
import com.devuloopers.knet.model.HttpResponse
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import java.util.UUID

/**
 * Utility functions to map Netty HTTP objects to KNet domain models.
 */
object HttpMapper {

    /**
     * Maps Netty's [FullHttpRequest] to KNet's domain [HttpRequest] model.
     *
     * @param nettyReq The Netty request object.
     * @param host The target host.
     * @param isSsl True if request is HTTPS.
     * @return Mapped [HttpRequest] instance.
     */
    fun mapRequest(nettyReq: FullHttpRequest, host: String, isSsl: Boolean): HttpRequest {
        val headers = mapHeaders(nettyReq.headers())
        val body = extractBody(nettyReq.content())
        val scheme = if (isSsl) "https" else "http"
        val fullUrl = if (nettyReq.uri().startsWith("http")) nettyReq.uri() else "$scheme://$host${nettyReq.uri()}"
        return HttpRequest(
            id = UUID.randomUUID().toString(),
            method = nettyReq.method().name(),
            url = fullUrl,
            protocol = nettyReq.protocolVersion().text(),
            headers = headers,
            body = body,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Maps Netty's [FullHttpResponse] to KNet's domain [HttpResponse] model.
     *
     * @param nettyRes The Netty response object.
     * @return Mapped [HttpResponse] instance.
     */
    fun mapResponse(nettyRes: FullHttpResponse): HttpResponse {
        val headers = mapHeaders(nettyRes.headers())
        val body = extractBody(nettyRes.content())
        return HttpResponse(
            statusCode = nettyRes.status().code(),
            statusText = nettyRes.status().reasonPhrase(),
            headers = headers,
            body = body,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Converts Netty [HttpHeaders] to a list of header key-value pairs.
     */
    private fun mapHeaders(headers: HttpHeaders): List<Pair<String, String>> {
        return headers.map { Pair(it.key, it.value) }
    }

    /**
     * Extracts byte contents from a Netty [ByteBuf] message body if available.
     */
    private fun extractBody(content: ByteBuf): ByteArray? {
        return if (content.readableBytes() > 0) {
            val bytes = ByteArray(content.readableBytes())
            content.getBytes(content.readerIndex(), bytes)
            bytes
        } else null
    }
}
