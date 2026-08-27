package com.devuloopers.knet.engine.proxy.pipeline

import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import io.netty.util.AttributeKey

/** Capture handle admitted before an optional forwarding gate suspends the exchange. */
data class PreparedProxyExchange(
    /** Stable identifier shared by capture, breakpoint coordination, and forwarding. */
    val exchangeId: ExchangeId,
    /** Nullable side-output handle when capture admission was intentionally unavailable. */
    val capture: ProxyExchangeCapture?,
)

/** Cross-handler channel attributes owned by the proxy transport module. */
object ProxyChannelAttributes {
    /** CONNECT authority retained solely as the destination used for upstream socket routing. */
    val ROUTE_HOST: AttributeKey<String> = AttributeKey.valueOf("knet.routeHost")

    /** ClientHello SNI selected for certificate identity and upstream TLS, never for socket routing. */
    val TLS_SERVER_NAME: AttributeKey<String> = AttributeKey.valueOf("knet.tlsServerName")

    /** CONNECT authority port retained for upstream connection establishment. */
    val PORT: AttributeKey<Int> = AttributeKey.valueOf("knet.port")

    /** Whether this client channel has entered the decrypted TLS pipeline. */
    val IS_SSL: AttributeKey<Boolean> = AttributeKey.valueOf("knet.ssl")

    /** Fresh canonical request context for the request currently crossing the pipeline. */
    val REQUEST_CONTEXT: AttributeKey<ProxyRequestContext> = AttributeKey.valueOf("knet.request")

    /** Connection-scoped non-blocking capture side output supplied by the proxy server. */
    val CONNECTION_CAPTURE: AttributeKey<ProxyConnectionCapture> = AttributeKey.valueOf("knet.connectionCapture")

    /** One-shot exchange capture handoff consumed by the forwarding handler. */
    val PREPARED_EXCHANGE: AttributeKey<PreparedProxyExchange> = AttributeKey.valueOf("knet.preparedExchange")

    /** Native multiplexed stream identity; absent for connection-ordered HTTP/1 traffic. */
    val STREAM_ID: AttributeKey<StreamId> = AttributeKey.valueOf("knet.streamId")

    /** Negotiated downstream application protocol when an object bridge hides the wire version. */
    val APPLICATION_PROTOCOL: AttributeKey<ApplicationProtocol> = AttributeKey.valueOf("knet.applicationProtocol")

    /** Observed upstream response protocol when an object bridge hides the wire version. */
    val UPSTREAM_APPLICATION_PROTOCOL: AttributeKey<ApplicationProtocol> =
        AttributeKey.valueOf("knet.upstreamApplicationProtocol")
}
