package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import java.net.URI

private const val TAG = "KNetInterceptorHandler"

/**
 * Netty duplex handler that intercepts inbound client requests and outbound server responses.
 * Delegates rule matching to [BreakpointMatcher] and suspension coordination to [InterceptCoordinator].
 */
@Suppress("HttpUrlsUsage")
class KNetInterceptorHandler(
    private val listener: ProxyTrafficListener? = null
) : ChannelDuplexHandler() {

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg is FullHttpRequest) {
            // Bypass CONNECT handshakes - let KNetProxyHandler handle TLS setup
            if (msg.method() == HttpMethod.CONNECT) {
                super.channelRead(context, msg)
                return
            }

            // Always map incoming request freshly to prevent stale attribute leakage across pipelined/reused connections
            val isSsl = context.channel().attr(ChannelAttributes.SSL_ATTR).get() == true || context.pipeline().get("ssl") != null
            var targetHost = context.channel().attr(ChannelAttributes.HOST_ATTR).get()

            if (targetHost == null) {
                val uri = msg.uri()
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    try {
                        val urlObj = URI.create(uri)
                        targetHost = urlObj.host
                    } catch (_: Exception) {
                        // Fallback to Host header
                    }
                }
                if (targetHost == null) {
                    val hostHeader = msg.headers().get("Host")
                    if (hostHeader != null) {
                        targetHost = hostHeader.split(":")[0]
                    }
                }
            }

            val host = targetHost ?: "unknown"
            val request = HttpMapper.mapRequest(msg, host, isSsl)
            context.channel().attr(ChannelAttributes.REQUEST_ATTR).set(request)

            val requestBodyText = decodeBodyToText(request.body)
            val rule = BreakpointMatcher.findMatchingRequestRule(request.url, request.method, requestBodyText)
            if (rule != null) {
                KNetLogger.info(TAG) {
                    "Breakpoint hit for request: ${request.method} ${request.url} [rule=${rule.id}]"
                }

                val taggedRequest = HttpRequest(
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

                // Immediately capture in-progress request so it appears in the traffic table while paused
                listener?.onRequestCaptured(taggedRequest)

                InterceptCoordinator.coordinateRequest(context, msg, taggedRequest, listener)
                return
            }
        }
        super.channelRead(context, msg)
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is FullHttpResponse) {
            val request = context.channel().attr(ChannelAttributes.REQUEST_ATTR).get()
            if (request != null) {
                val requestBodyText = decodeBodyToText(request.body)
                val rule = BreakpointMatcher.findMatchingResponseRule(request.url, request.method, requestBodyText)
                if (rule != null) {
                    KNetLogger.info(TAG) {
                        "Breakpoint hit for response: ${request.method} ${request.url} -> ${msg.status().code()} [rule=${rule.id}]"
                    }

                    val taggedRequest = HttpRequest(
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
                    InterceptCoordinator.coordinateResponse(context, msg, taggedRequest, mappedResponse, listener)
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
