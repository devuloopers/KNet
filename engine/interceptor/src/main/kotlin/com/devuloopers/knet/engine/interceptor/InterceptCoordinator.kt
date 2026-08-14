package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
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
        request: HttpRequest,
        listener: ProxyTrafficListener? = null
    ) {
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
                KNetLogger.error(TAG) { "Exception during request suspension await: ${e.message}" }
                InterceptResult.Drop
            } finally {
                InterceptSessionManager.resume(event.id, InterceptResult.Drop)
            }

            try {
                when (result) {
                    is InterceptResult.Resume -> {
                        val modified = result.modifiedRequest
                        val reqToResume = if (modified != null) {
                            HttpRequest(
                                id = modified.id,
                                method = modified.method,
                                url = modified.url,
                                protocol = modified.protocol,
                                headers = modified.headers,
                                body = modified.body,
                                timestamp = modified.timestamp,
                                isIntercepted = true,
                                matchedRuleId = modified.matchedRuleId ?: request.matchedRuleId
                            )
                        } else {
                            request
                        }
                        context.channel().attr(ChannelAttributes.REQUEST_ATTR).set(reqToResume)

                        if (modified != null) {
                            val rebuilt = RequestRebuilder.rebuild(msg, reqToResume)
                            context.fireChannelRead(rebuilt)
                            ReferenceCountUtil.release(msg)
                        } else {
                            context.fireChannelRead(msg)
                        }
                        context.channel().config().isAutoRead = true
                    }
                    is InterceptResult.Drop -> {
                        KNetLogger.info(TAG) { "Dropping request ${request.id} by user request" }
                        listener?.onTransactionDropped(request.id, "Dropped")
                        ReferenceCountUtil.release(msg)
                        context.channel().config().isAutoRead = true
                        context.close()
                    }
                    is InterceptResult.Timeout -> {
                        KNetLogger.warn(TAG) { "Request ${request.id} timed out after ${timeoutMs}ms" }
                        listener?.onTransactionDropped(request.id, "Timed Out")
                        ReferenceCountUtil.release(msg)
                        context.channel().config().isAutoRead = true
                        context.close()
                    }
                }
            } catch (e: Exception) {
                KNetLogger.error(TAG) { "Error firing resumed request: ${e.message}" }
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
        mappedResponse: HttpResponse,
        listener: ProxyTrafficListener? = null
    ) {
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
                KNetLogger.error(TAG) { "Exception during response suspension await: ${e.message}" }
                InterceptResult.Drop
            } finally {
                InterceptSessionManager.resume(event.id, InterceptResult.Drop)
            }

            try {
                when (result) {
                    is InterceptResult.Resume -> {
                        val modified = result.modifiedResponse
                        val responseToPersist = modified ?: mappedResponse
                        val totalDuration = (System.currentTimeMillis() - request.timestamp).coerceAtLeast(0L)

                        listener?.onResponseCaptured(
                            transactionId = request.id,
                            response = responseToPersist,
                            durationMs = totalDuration,
                            timings = com.devuloopers.knet.domain.clientNetwork.model.HttpTimings()
                        )

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
                    is InterceptResult.Drop -> {
                        KNetLogger.info(TAG) { "Dropping response ${request.id} by user request" }
                        listener?.onTransactionDropped(request.id, "Dropped")
                        ReferenceCountUtil.release(msg)
                        context.channel().config().isAutoRead = true
                        context.close()
                    }
                    is InterceptResult.Timeout -> {
                        KNetLogger.warn(TAG) { "Response ${request.id} timed out after ${timeoutMs}ms" }
                        listener?.onTransactionDropped(request.id, "Timed Out")
                        ReferenceCountUtil.release(msg)
                        context.channel().config().isAutoRead = true
                        context.close()
                    }
                }
            } catch (e: Exception) {
                KNetLogger.error(TAG) { "Error firing resumed response: ${e.message}" }
                ReferenceCountUtil.release(msg)
                context.channel().config().isAutoRead = true
                context.close()
            }
        }
    }
}
