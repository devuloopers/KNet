package com.devuloopers.knet.engine.proxy.pipeline

import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.traffic.id.ExchangeId
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
    /** Fresh canonical request context for the request currently crossing the pipeline. */
    val REQUEST_CONTEXT: AttributeKey<ProxyRequestContext> = AttributeKey.valueOf("knet.request")

    /** Connection-scoped non-blocking capture side output supplied by the proxy server. */
    val CONNECTION_CAPTURE: AttributeKey<ProxyConnectionCapture> = AttributeKey.valueOf("knet.connectionCapture")

    /** One-shot exchange capture handoff consumed by the forwarding handler. */
    val PREPARED_EXCHANGE: AttributeKey<PreparedProxyExchange> = AttributeKey.valueOf("knet.preparedExchange")
}
