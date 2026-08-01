package com.devuloopers.knet.engine.traffic

import com.devuloopers.knet.engine.traffic.processors.MapLocalProcessor
import com.devuloopers.knet.engine.traffic.processors.MapRemoteProcessor
import com.devuloopers.knet.engine.traffic.processors.RequestModifierProcessor
import com.devuloopers.knet.engine.traffic.processors.ResponseModifierProcessor
import com.devuloopers.knet.core.logger.KNetLogger
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.util.AttributeKey

private const val TAG = "KNetTrafficModifierHandler"

/**
 * Netty [ChannelDuplexHandler] orchestrator that applies active traffic modification rules.
 *
 * This handler must be registered **before** `proxyHandler` in the server pipeline so that
 * modifications are applied before the proxy engine forwards them upstream.
 *
 * Supported operations:
 * - **Map Local**: Serve a local file response immediately without hitting the network.
 * - **Map Remote**: Transparently re-route to an alternate target host and port.
 * - **Modifier Rules**: Mutate headers, query parameters, body content, or response status codes.
 *
 * @property manager The [TrafficModifierManager] containing active rule sets.
 */
class KNetTrafficModifierHandler(
    private val manager: TrafficModifierManager
) : ChannelDuplexHandler() {

    companion object {
        /** Channel attribute key storing the target hostname for the proxy engine. */
        val HOST_ATTR: AttributeKey<String> = MapRemoteProcessor.HOST_ATTR

        /** Channel attribute key storing the target port for the proxy engine. */
        val PORT_ATTR: AttributeKey<Int> = MapRemoteProcessor.PORT_ATTR
    }

    /**
     * Intercepts inbound HTTP requests. Evaluates Map Local short-circuits, Map Remote re-routing,
     * and request-side Modifier Rules before forwarding the request downstream.
     */
    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg !is FullHttpRequest) {
            context.fireChannelRead(msg)
            return
        }

        val url = resolveFullUrl(context, msg)
        KNetLogger.debug(TAG) { "Evaluating traffic modifier rules for: $url" }

        // 1. Map Local Processor
        val handledLocally = MapLocalProcessor.process(context, msg, url, manager.getMapLocalRules())
        if (handledLocally) return

        // 2. Map Remote Processor
        MapRemoteProcessor.process(context, msg, url, manager.getMapRemoteRules())

        // 3. Request Modifier Processor
        RequestModifierProcessor.process(msg, url, manager.getModifierRules())

        context.fireChannelRead(msg)
    }

    /**
     * Intercepts outbound HTTP responses. Evaluates response-side Modifier Rules before
     * writing the response back to the client channel.
     */
    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is FullHttpResponse) {
            context.write(msg, promise)
            return
        }

        ResponseModifierProcessor.process(msg, manager.getModifierRules())
        context.write(msg, promise)
    }

    private fun resolveFullUrl(context: ChannelHandlerContext, msg: FullHttpRequest): String {
        val uri = msg.uri()
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
        val host = context.channel().attr(HOST_ATTR).get() ?: ""
        val port = context.channel().attr(PORT_ATTR).get() ?: 443
        val isSsl = port == 443 || port == 8443
        val scheme = if (isSsl) "https" else "http"
        return "$scheme://$host$uri"
    }
}
