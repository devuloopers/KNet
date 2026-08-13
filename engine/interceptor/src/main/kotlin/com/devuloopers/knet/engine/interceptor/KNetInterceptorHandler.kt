package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import java.net.URI

/**
 * Netty duplex handler that intercepts inbound client requests and outbound server responses.
 * Delegates rule matching to [BreakpointMatcher] and suspension coordination to [InterceptCoordinator].
 */
@Suppress("HttpUrlsUsage")
class KNetInterceptorHandler : ChannelDuplexHandler() {

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg is FullHttpRequest) {
            var request = context.channel().attr(ChannelAttributes.REQUEST_ATTR).get()
            if (request == null) {
                val isSsl = context.channel().attr(ChannelAttributes.SSL_ATTR).get() ?: false
                var targetHost = context.channel().attr(ChannelAttributes.HOST_ATTR).get()
                if (targetHost == null) {
                    val uri = msg.uri()
                    if (uri.startsWith("http://")) {
                        val urlObj = URI.create(uri).toURL()
                        targetHost = urlObj.host
                    } else {
                        val hostHeader = msg.headers().get("Host")
                        if (hostHeader != null) {
                            targetHost = hostHeader.split(":")[0]
                        }
                    }
                }
                if (targetHost != null) {
                    request = HttpMapper.mapRequest(msg, targetHost, isSsl)
                    context.channel().attr(ChannelAttributes.REQUEST_ATTR).set(request)
                }
            }

            if (request != null) {
                val requestBodyText = com.devuloopers.knet.domain.util.decodeBodyToText(request.body)
                val rule = BreakpointMatcher.findMatchingRequestRule(request.url, request.method, requestBodyText)
                if (rule != null) {
                    val taggedRequest = com.devuloopers.knet.domain.clientNetwork.model.HttpRequest(
                        id = request.id,
                        method = request.method,
                        url = request.url,
                        protocol = request.protocol,
                        headers = request.headers,
                        body = request.body,
                        timestamp = request.timestamp,
                        isIntercepted = true,
                        matchedRuleId = rule.id
                    )
                    context.channel().attr(ChannelAttributes.REQUEST_ATTR).set(taggedRequest)
                    InterceptCoordinator.coordinateRequest(context, msg, taggedRequest)
                    return
                }
            }
        }
        super.channelRead(context, msg)
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is FullHttpResponse) {
            val request = context.channel().attr(ChannelAttributes.REQUEST_ATTR).get()
            if (request != null) {
                val requestBodyText = com.devuloopers.knet.domain.util.decodeBodyToText(request.body)
                val rule = BreakpointMatcher.findMatchingResponseRule(request.url, request.method, requestBodyText)
                if (rule != null) {
                    val taggedRequest = com.devuloopers.knet.domain.clientNetwork.model.HttpRequest(
                        id = request.id,
                        method = request.method,
                        url = request.url,
                        protocol = request.protocol,
                        headers = request.headers,
                        body = request.body,
                        timestamp = request.timestamp,
                        isIntercepted = true,
                        matchedRuleId = rule.id
                    )
                    context.channel().attr(ChannelAttributes.REQUEST_ATTR).set(taggedRequest)
                    val mappedResponse = HttpMapper.mapResponse(msg)
                    InterceptCoordinator.coordinateResponse(context, msg, taggedRequest, mappedResponse)
                    return
                }
            }
        }
        super.write(context, msg, promise)
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        val request = context.channel().attr(ChannelAttributes.REQUEST_ATTR).get()
        if (request != null) {
            InterceptSessionManager.getActiveEvents()
                .filter { it.request.id == request.id }
                .forEach { InterceptSessionManager.resume(it.id, InterceptResult.Drop) }
        }
        super.channelInactive(context)
    }

    override fun handlerRemoved(context: ChannelHandlerContext) {
        super.handlerRemoved(context)
    }
}
