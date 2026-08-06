package com.devuloopers.knet.engine.proxy.mapper

import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
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
     */
    fun mapRequest(nettyReq: FullHttpRequest, host: String, isSsl: Boolean): HttpRequest {
        return mapRequest(nettyReq, isSsl, host, if (isSsl) 443 else 80, nettyReq.uri())
    }

    /**
     * Maps Netty's [FullHttpRequest] to KNet's domain [HttpRequest] model with explicit port and relative URI.
     */
    fun mapRequest(
        nettyReq: FullHttpRequest,
        isSsl: Boolean,
        host: String,
        port: Int,
        relativeUri: String
    ): HttpRequest {
        val headers = mapHeaders(nettyReq.headers())
        val body = extractBody(nettyReq.content())
        val scheme = if (isSsl) "https" else "http"
        val portSuffix = if ((isSsl && port == 443) || (!isSsl && port == 80)) "" else ":$port"
        val fullUrl = if (nettyReq.uri().startsWith("http")) nettyReq.uri() else "$scheme://$host$portSuffix$relativeUri"
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
     * Maps Netty's streaming [io.netty.handler.codec.http.HttpResponse] headers to KNet's domain [HttpResponse] model.
     */
    fun mapResponseHeaders(nettyRes: io.netty.handler.codec.http.HttpResponse): HttpResponse {
        val headers = mapHeaders(nettyRes.headers())
        return HttpResponse(
            statusCode = nettyRes.status().code(),
            statusText = nettyRes.status().reasonPhrase(),
            headers = headers,
            body = null,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun mapHeaders(headers: HttpHeaders): List<Pair<String, String>> {
        return headers.map { Pair(it.key, it.value) }
    }

    private fun extractBody(content: ByteBuf): ByteArray? {
        return if (content.readableBytes() > 0) {
            val bytes = ByteArray(content.readableBytes())
            content.getBytes(content.readerIndex(), bytes)
            bytes
        } else null
    }
}
