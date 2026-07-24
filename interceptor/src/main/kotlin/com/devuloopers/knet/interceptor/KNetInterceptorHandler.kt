package com.devuloopers.knet.interceptor

import com.devuloopers.knet.engine.util.HttpMapper
import com.devuloopers.knet.logger.KNetLogger
import com.devuloopers.knet.model.HttpRequest
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.net.URI

private const val TAG = "Interceptor"

/**
 * Netty duplex handler that intercepts inbound client requests and outbound server responses.
 * Implements non-blocking backpressure and coroutine suspension to pause traffic matching breakpoint rules.
 */
@Suppress("HttpUrlsUsage")
class KNetInterceptorHandler : ChannelDuplexHandler() {

    companion object {
        private val REQUEST_ATTR = AttributeKey.valueOf<HttpRequest>("knet.request")
        private val HOST_ATTR = AttributeKey.valueOf<String>("knet.host")
        private val SSL_ATTR = AttributeKey.valueOf<Boolean>("knet.ssl")
    }

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg is FullHttpRequest) {
            var request = context.channel().attr(REQUEST_ATTR).get()
            if (request == null) {
                val isSsl = context.channel().attr(SSL_ATTR).get() ?: false
                var targetHost = context.channel().attr(HOST_ATTR).get()
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
                    context.channel().attr(REQUEST_ATTR).set(request)
                }
            }

            if (request != null) {
                val rule = BreakpointManager.findMatchingRequestRule(request.url, request.method)
                if (rule != null) {
                    KNetLogger.info(TAG) { "Breakpoint hit for request: ${request.method} ${request.url}" }
                    
                    // Stop Netty from reading more data from the socket (backpressure)
                    context.channel().config().isAutoRead = false
                    
                    // Retain the message reference so Netty does not release it while paused
                    ReferenceCountUtil.retain(msg)

                    // Suspend and await resolution on the Netty EventLoop thread dispatcher
                    val event = BreakpointManager.suspendRequest(request)
                    val dispatcher = context.executor().asCoroutineDispatcher()
                    CoroutineScope(SupervisorJob() + dispatcher).launch {
                        when (val result = event.deferred.await()) {
                            is InterceptResult.Resume -> {
                                val modified = result.modifiedRequest
                                if (modified != null) {
                                    val rebuilt = rebuildNettyRequest(msg, modified)
                                    // Update cached request context
                                    context.channel().attr(REQUEST_ATTR).set(modified)
                                    context.fireChannelRead(rebuilt)
                                    ReferenceCountUtil.release(msg)
                                } else {
                                    context.fireChannelRead(msg)
                                }
                                // Restore auto-read to resume network flow
                                context.channel().config().isAutoRead = true
                            }
                            is InterceptResult.Drop -> {
                                ReferenceCountUtil.release(msg)
                                context.close()
                            }
                        }
                    }
                    return
                }
            }
        }
        super.channelRead(context, msg)
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is FullHttpResponse) {
            val request = context.channel().attr(REQUEST_ATTR).get()
            if (request != null) {
                val rule = BreakpointManager.findMatchingResponseRule(request.url, request.method)
                if (rule != null) {
                    val mappedResponse = HttpMapper.mapResponse(msg)
                    KNetLogger.info(TAG) { "Breakpoint hit for response: ${request.method} ${request.url} -> ${mappedResponse.statusCode}" }

                    // Stop reading incoming frames
                    context.channel().config().isAutoRead = false
                    
                    // Retain response buffer
                    ReferenceCountUtil.retain(msg)

                    val event = BreakpointManager.suspendResponse(request, mappedResponse)
                    val dispatcher = context.executor().asCoroutineDispatcher()
                    CoroutineScope(SupervisorJob() + dispatcher).launch {
                        when (val result = event.deferred.await()) {
                            is InterceptResult.Resume -> {
                                val modified = result.modifiedResponse
                                if (modified != null) {
                                    val rebuilt = rebuildNettyResponse(msg, modified)
                                    context.write(rebuilt, promise)
                                    ReferenceCountUtil.release(msg)
                                } else {
                                    context.write(msg, promise)
                                }
                                context.channel().config().isAutoRead = true
                                context.flush()
                            }
                            is InterceptResult.Drop -> {
                                ReferenceCountUtil.release(msg)
                                context.close()
                            }
                        }
                    }
                    return
                }
            }
        }
        super.write(context, msg, promise)
    }

    /**
     * Converts the common HttpRequest DTO back to Netty's FullHttpRequest.
     */
    private fun rebuildNettyRequest(original: FullHttpRequest, modified: HttpRequest): FullHttpRequest {
        val content = if (modified.body != null) {
            Unpooled.copiedBuffer(modified.body)
        } else {
            Unpooled.EMPTY_BUFFER
        }
        val rebuilt = DefaultFullHttpRequest(
            original.protocolVersion(),
            io.netty.handler.codec.http.HttpMethod.valueOf(modified.method),
            extractRelativeUri(modified.url),
            content
        )
        rebuilt.headers().clear()
        modified.headers.forEach { (key, value) ->
            rebuilt.headers().add(key, value)
        }
        rebuilt.headers().set(io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return rebuilt
    }

    /**
     * Converts the common HttpResponse DTO back to Netty's FullHttpResponse.
     */
    private fun rebuildNettyResponse(original: FullHttpResponse, modified: com.devuloopers.knet.model.HttpResponse): FullHttpResponse {
        val content = if (modified.body != null) {
            Unpooled.copiedBuffer(modified.body)
        } else {
            Unpooled.EMPTY_BUFFER
        }
        val rebuilt = DefaultFullHttpResponse(
            original.protocolVersion(),
            io.netty.handler.codec.http.HttpResponseStatus.valueOf(modified.statusCode),
            content
        )
        rebuilt.headers().clear()
        modified.headers.forEach { (key, value) ->
            rebuilt.headers().add(key, value)
        }
        rebuilt.headers().set(io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        return rebuilt
    }

    /**
     * Extracts the relative path and query from a full URL to form the HTTP request URI.
     */
    private fun extractRelativeUri(urlStr: String): String {
        return if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
            try {
                val uri = URI.create(urlStr)
                val path = uri.path.ifEmpty { "/" }
                val query = uri.rawQuery
                if (query != null) "$path?$query" else path
            } catch (_: Exception) {
                urlStr
            }
        } else {
            urlStr
        }
    }
}
