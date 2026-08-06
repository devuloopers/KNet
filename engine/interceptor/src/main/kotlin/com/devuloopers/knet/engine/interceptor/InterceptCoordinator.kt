package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "InterceptCoordinator"

/**
 * Coordinates suspend/resume lifecycle between Netty event loops and [InterceptSessionManager].
 * Manages TCP backpressure, buffer retention, coroutine completion, and timeout cleanup.
 */
object InterceptCoordinator {

    var timeoutMs: Long = 60_000L

    fun coordinateRequest(
        context: ChannelHandlerContext,
        msg: FullHttpRequest,
        request: HttpRequest
    ) {
        KNetLogger.info(TAG) { "Breakpoint hit for request: ${request.method} ${request.url}" }

        context.channel().config().isAutoRead = false
        ReferenceCountUtil.retain(msg)

        val event = InterceptSessionManager.suspendRequest(request)
        val dispatcher = context.executor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        scope.launch {
            val result = try {
                withTimeoutOrNull(timeoutMs) {
                    event.deferred.await()
                } ?: InterceptResult.Timeout
            } catch (e: Exception) {
                InterceptResult.Drop
            } finally {
                InterceptSessionManager.resume(event.id, InterceptResult.Drop)
            }

            try {
                when (result) {
                    is InterceptResult.Resume -> {
                        val modified = result.modifiedRequest
                        if (modified != null) {
                            val rebuilt = RequestRebuilder.rebuild(msg, modified)
                            context.channel().attr(ChannelAttributes.REQUEST_ATTR).set(modified)
                            context.fireChannelRead(rebuilt)
                            ReferenceCountUtil.release(msg)
                        } else {
                            context.fireChannelRead(msg)
                        }
                        context.channel().config().isAutoRead = true
                    }
                    is InterceptResult.Drop, is InterceptResult.Timeout -> {
                        ReferenceCountUtil.release(msg)
                        context.channel().config().isAutoRead = true
                        context.close()
                    }
                }
            } catch (e: Exception) {
                ReferenceCountUtil.release(msg)
                context.channel().config().isAutoRead = true
                context.close()
            }
        }
    }

    fun coordinateResponse(
        context: ChannelHandlerContext,
        msg: FullHttpResponse,
        request: HttpRequest,
        mappedResponse: HttpResponse
    ) {
        KNetLogger.info(TAG) { "Breakpoint hit for response: ${request.method} ${request.url} -> ${mappedResponse.statusCode}" }

        context.channel().config().isAutoRead = false
        ReferenceCountUtil.retain(msg)

        val event = InterceptSessionManager.suspendResponse(request, mappedResponse)
        val dispatcher = context.executor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        scope.launch {
            val result = try {
                withTimeoutOrNull(timeoutMs) {
                    event.deferred.await()
                } ?: InterceptResult.Timeout
            } catch (e: Exception) {
                InterceptResult.Drop
            } finally {
                InterceptSessionManager.resume(event.id, InterceptResult.Drop)
            }

            try {
                when (result) {
                    is InterceptResult.Resume -> {
                        val modified = result.modifiedResponse
                        if (modified != null) {
                            val rebuilt = ResponseRebuilder.rebuild(msg, modified)
                            context.write(rebuilt)
                            ReferenceCountUtil.release(msg)
                        } else {
                            context.write(msg)
                        }
                        context.channel().config().isAutoRead = true
                        context.flush()
                    }
                    is InterceptResult.Drop, is InterceptResult.Timeout -> {
                        ReferenceCountUtil.release(msg)
                        context.channel().config().isAutoRead = true
                        context.close()
                    }
                }
            } catch (e: Exception) {
                ReferenceCountUtil.release(msg)
                context.channel().config().isAutoRead = true
                context.close()
            }
        }
    }
}
