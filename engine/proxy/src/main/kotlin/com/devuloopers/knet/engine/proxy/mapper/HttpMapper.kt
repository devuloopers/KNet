package com.devuloopers.knet.engine.proxy.mapper

import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficAttributionHeader
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest as NettyHttpRequest
import io.netty.handler.codec.http.HttpResponse as nHttpResponse
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod as CanonicalHttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.HttpStatus
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import kotlin.time.Clock

/**
 * Utility functions to map Netty HTTP objects to KNet domain models.
 */
object HttpMapper {

    /** Maps a request head into its canonical snapshot and transport lifecycle envelope. */
    fun mapRequestContext(
        nettyReq: NettyHttpRequest,
        isSsl: Boolean,
        host: String,
        port: Int,
        relativeUri: String,
    ): ProxyRequestContext = ProxyRequestContext(
        exchangeId = ExchangeId(newExchangeId()),
        request = HttpRequestSnapshot(
            head = mapRequestHead(nettyReq, isSsl, host, port, relativeUri),
            body = MessageBodyRef.Empty,
        ),
        startedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        origin = nettyReq.headers()
            .get(TrafficAttributionHeader.NAME)
            ?.takeIf(String::isNotBlank)
            ?.let { token -> runCatching { TrafficOrigin.fromToken(token) }.getOrNull() }
            ?: TrafficOrigin.ProxyClient,
    )

    /** Maps transport request metadata into the shared canonical request head. */
    fun mapRequestHead(
        nettyRequest: NettyHttpRequest,
        isSsl: Boolean,
        host: String,
        port: Int,
        relativeUri: String,
    ): RequestHead = RequestHead(
        method = CanonicalHttpMethod.fromToken(nettyRequest.method().name()),
        target = RequestTarget.Absolute(
            scheme = HttpScheme.fromToken(if (isSsl) "https" else "http"),
            authority = Authority(host = host, port = port),
            pathAndQuery = relativeUri.takeIf { value -> value.startsWith('/') } ?: "/",
        ),
        protocol = ApplicationProtocol.fromToken(nettyRequest.protocolVersion().text()),
        headers = mapCanonicalHeaders(nettyRequest.headers()),
    )

    /** Maps transport response metadata into the shared canonical response head. */
    fun mapResponseHead(nettyResponse: nHttpResponse): ResponseHead = ResponseHead(
        protocol = ApplicationProtocol.fromToken(nettyResponse.protocolVersion().text()),
        status = HttpStatus(nettyResponse.status().code()),
        reasonPhrase = nettyResponse.status().reasonPhrase().takeIf(String::isNotBlank),
        headers = mapCanonicalHeaders(nettyResponse.headers()),
    )

    /** Returns the observed representation encoding without decoding body bytes. */
    fun contentEncoding(headers: HttpHeaders): ContentEncoding? = headers
        .get("Content-Encoding")
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(ContentEncoding::fromToken)

    /** Removes local attribution metadata before forwarding a request to an upstream server. */
    fun removeCaptureAttribution(request: NettyHttpRequest) {
        request.headers().remove(TrafficAttributionHeader.NAME)
    }

    /** Allocates the transport-owned stable exchange ID. */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun newExchangeId(): String = kotlin.uuid.Uuid.random().toString()

    private fun mapCanonicalHeaders(headers: HttpHeaders): List<HeaderField> = headers
        .asSequence()
        .filterNot { header -> header.key.equals(TrafficAttributionHeader.NAME, ignoreCase = true) }
        .map { header -> HeaderField(HeaderName(header.key), header.value) }
        .toList()
}
