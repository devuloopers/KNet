package com.devuloopers.knet.engine.traffic.processors

import com.devuloopers.knet.engine.traffic.MapRemoteRule
import com.devuloopers.knet.engine.traffic.RegexCache
import com.devuloopers.knet.core.logger.KNetLogger
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.util.AttributeKey

private const val TAG = "MapRemoteProcessor"

internal object MapRemoteProcessor {

    val HOST_ATTR: AttributeKey<String> = AttributeKey.valueOf("knet.host")
    val PORT_ATTR: AttributeKey<Int> = AttributeKey.valueOf("knet.port")

    /**
     * Evaluates active Map Remote rules against the request URL.
     * If matched, re-routes the Netty channel attributes and HTTP Host header.
     */
    fun process(
        context: ChannelHandlerContext,
        request: FullHttpRequest,
        url: String,
        rules: List<MapRemoteRule>
    ) {
        val matchedRule = rules.firstOrNull { rule ->
            rule.enabled && RegexCache.getOrNull(rule.urlPattern)?.containsMatchIn(url) == true
        } ?: return

        KNetLogger.debug(TAG) { "Map Remote matched [${matchedRule.id}]: redirecting to ${matchedRule.targetHost}:${matchedRule.targetPort}" }
        context.channel().attr(HOST_ATTR).set(matchedRule.targetHost)
        context.channel().attr(PORT_ATTR).set(matchedRule.targetPort)
        request.headers().set(HttpHeaderNames.HOST, matchedRule.targetHost)
    }
}
