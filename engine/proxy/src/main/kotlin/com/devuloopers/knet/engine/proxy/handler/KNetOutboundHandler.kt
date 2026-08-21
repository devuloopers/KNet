package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.http.HttpOneDownstreamPolicy
import com.devuloopers.knet.engine.proxy.http.HttpOneSemantics
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.timing.NetworkTimingCollector
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.LastHttpContent
import io.netty.util.ReferenceCountUtil
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock

private const val OUTBOUND_TAG = "ProxyEngine"

/**
 * Streams one upstream HTTP/1 response to its downstream client with explicit backpressure.
 *
 * The handler owns the upstream response objects delivered to [channelRead0], retains exactly one
 * reference for each downstream write, and advances upstream reads only after the previous decoded
 * batch has been accepted by the downstream channel. Capture reaches a terminal state only after
 * the final downstream write succeeds or fails.
 *
 * @param clientChannel Downstream client channel receiving the response.
 * @param request Already-framed upstream request head written when the channel becomes active.
 * @param timingCollector Per-exchange network timing collector.
 * @param downstreamPolicy Client-facing HTTP/1 version and persistence decision captured before forwarding.
 * @param onExchangeComplete Idempotent owner callback invoked with downstream reusability after terminal delivery
 * or failure.
 * @param capture Optional persistence-neutral capture side output.
 * @param onRequestHeadWritten Callback providing the active upstream channel after head delivery.
 * @param onUpstreamWritable Callback used by the request pump when upstream writability returns.
 */
internal class KNetOutboundHandler(
    private val clientChannel: Channel,
    private val request: HttpRequest,
    private val timingCollector: NetworkTimingCollector = NetworkTimingCollector(),
    private val downstreamPolicy: HttpOneDownstreamPolicy = HttpOneSemantics.downstreamPolicy(request),
    private val onExchangeComplete: (Boolean) -> Unit = {},
    private val capture: ProxyExchangeCapture? = null,
    private val onRequestHeadWritten: (Channel) -> Unit = {},
    private val onUpstreamWritable: () -> Unit = {},
) : SimpleChannelInboundHandler<HttpObject>() {
    private var responseStarted: Boolean = false
    private var isKeepAlive: Boolean = true
    private val completionPublished = AtomicBoolean(false)
    private var lastClientWrite: io.netty.channel.ChannelFuture? = null
    private var responseComplete: Boolean = false
    private var provisionalResponseInProgress: Boolean = false
    private var responseObservedBytes: Long = 0L
    private var responseContentEncoding: ContentEncoding? = null

    override fun channelActive(context: ChannelHandlerContext) {
        context.writeAndFlush(request).addListener { writeFuture ->
            if (writeFuture.isSuccess) {
                timingCollector.markRequestSent()
                onRequestHeadWritten(context.channel())
                context.read()
            } else {
                context.fireExceptionCaught(
                    writeFuture.cause() ?: IOException("Failed to write request to upstream."),
                )
            }
        }
    }

    override fun channelWritabilityChanged(context: ChannelHandlerContext) {
        if (context.channel().isWritable) onUpstreamWritable()
        super.channelWritabilityChanged(context)
    }

    override fun channelRead0(context: ChannelHandlerContext, message: HttpObject) {
        when (message) {
            is FullHttpResponse -> acceptFullResponse(context, message)
            is HttpResponse -> acceptResponseHead(message)
            is HttpContent -> acceptResponseContent(context, message)
        }
    }

    private fun acceptFullResponse(context: ChannelHandlerContext, response: FullHttpResponse) {
        if (response.status().code() in 100..199 && response.status().code() != 101) {
            HttpOneSemantics.prepareProvisionalResponse(response, downstreamPolicy)
            KNetLogger.debug(OUTBOUND_TAG) { "KNet Proxy Provisional Full Response: ${response.status()}" }
            lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(response))
            return
        }
        timingCollector.markFirstByteReceived()
        timingCollector.markLastByteReceived()
        responseStarted = true
        val upstreamResponseHead = HttpMapper.mapResponseHead(response)
        isKeepAlive = HttpOneSemantics.prepareFinalResponse(
            response = response,
            requestMethod = request.method(),
            policy = downstreamPolicy,
        )
        KNetLogger.info(OUTBOUND_TAG) { "KNet Proxy Full Response: ${response.status()}" }

        val completedAt = Clock.System.now().toEpochMilliseconds()
        capture?.observeResponse(upstreamResponseHead, completedAt)
        captureBodyChunk(
            exchange = capture,
            direction = TrafficDirection.SERVER_TO_CLIENT,
            content = response.content(),
            contentEncoding = HttpMapper.contentEncoding(response.headers()),
        )
        capture?.completeBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = response.content().readableBytes().toLong(),
            occurredAtEpochMillis = completedAt,
        )
        responseComplete = true
        val timings = timingCollector.getTimings()
        lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(response)).addListener { future ->
            terminateAfterDownstreamWrite(future.isSuccess, timings, completedAt)
            publishCompletion(isKeepAlive)
            if (!isKeepAlive) clientChannel.close()
            context.close()
        }
    }

    private fun acceptResponseHead(response: HttpResponse) {
        provisionalResponseInProgress = response.status().code() in 100..199 && response.status().code() != 101
        if (provisionalResponseInProgress) {
            HttpOneSemantics.prepareProvisionalResponse(response, downstreamPolicy)
            KNetLogger.debug(OUTBOUND_TAG) { "KNet Proxy Provisional Response Headers: ${response.status()}" }
            lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(response))
            return
        }

        timingCollector.markFirstByteReceived()
        responseStarted = true
        responseContentEncoding = HttpMapper.contentEncoding(response.headers())
        val upstreamResponseHead = HttpMapper.mapResponseHead(response)
        isKeepAlive = HttpOneSemantics.prepareFinalResponse(
            response = response,
            requestMethod = request.method(),
            policy = downstreamPolicy,
        )
        capture?.observeResponse(upstreamResponseHead, Clock.System.now().toEpochMilliseconds())
        KNetLogger.info(OUTBOUND_TAG) { "KNet Proxy Response Headers: ${response.status()}" }
        lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(response))
    }

    private fun acceptResponseContent(context: ChannelHandlerContext, content: HttpContent) {
        if (provisionalResponseInProgress) {
            if (content is LastHttpContent) HttpOneSemantics.prepareFinalContent(content, downstreamPolicy)
            lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(content))
            if (content is LastHttpContent) provisionalResponseInProgress = false
            return
        }

        val readableBytes = content.content().readableBytes()
        responseObservedBytes += readableBytes.toLong()
        captureBodyChunk(
            exchange = capture,
            direction = TrafficDirection.SERVER_TO_CLIENT,
            content = content.content(),
            contentEncoding = responseContentEncoding,
        )
        if (content is LastHttpContent) HttpOneSemantics.prepareFinalContent(content, downstreamPolicy)
        val clientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(content))
        lastClientWrite = clientWrite
        if (content !is LastHttpContent) return

        timingCollector.markLastByteReceived()
        val timings = timingCollector.getTimings()
        val completedAt = Clock.System.now().toEpochMilliseconds()
        capture?.completeBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = responseObservedBytes,
            occurredAtEpochMillis = completedAt,
        )
        responseComplete = true
        clientWrite.addListener { future ->
            terminateAfterDownstreamWrite(future.isSuccess, timings, completedAt)
            publishCompletion(isKeepAlive)
            if (!isKeepAlive) clientChannel.close()
            // Upstream channels are deliberately one exchange at a time until a bounded reuse pool
            // is introduced behind this ownership point.
            context.close()
        }
    }

    /** Advances upstream reads only after the latest downstream write in this decoded batch. */
    override fun channelReadComplete(context: ChannelHandlerContext) {
        val downstreamWrite = lastClientWrite
        lastClientWrite = null
        if (!responseComplete) {
            if (downstreamWrite == null) {
                context.read()
            } else {
                downstreamWrite.addListener { future ->
                    if (future.isSuccess && context.channel().isActive) {
                        context.read()
                    } else if (!future.isSuccess) {
                        context.close()
                    }
                }
            }
        }
        super.channelReadComplete(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        if (cause is IOException) {
            KNetLogger.debug(OUTBOUND_TAG) { "KNet Outbound IO Exception: ${cause.message}" }
        } else {
            KNetLogger.error(OUTBOUND_TAG, cause) { "KNet Outbound Exception: ${cause.message}" }
        }

        if (!responseStarted) {
            sendSyntheticBadGateway("Outbound Proxy Exception: ${cause.message ?: cause::class.simpleName}")
        } else if (!responseComplete) {
            terminatePartialResponse()
        }
        context.close()
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        if (!responseComplete && !completionPublished.get()) {
            if (responseStarted) {
                terminatePartialResponse()
            } else {
                sendSyntheticBadGateway("Upstream connection closed before a response.")
            }
        }
        super.channelInactive(context)
    }

    /** Publishes a synthetic terminal response for an upstream failure before response headers. */
    private fun sendSyntheticBadGateway(message: String) {
        if (responseComplete || completionPublished.get()) return
        val body = "502 Bad Gateway: $message".toByteArray(Charsets.UTF_8)
        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(downstreamPolicy.version),
            HttpResponseStatus.BAD_GATEWAY,
            Unpooled.copiedBuffer(body),
        )
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.size)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)

        val failedAt = Clock.System.now().toEpochMilliseconds()
        capture?.observeResponse(HttpMapper.mapResponseHead(response), failedAt)
        captureBodyChunk(
            exchange = capture,
            direction = TrafficDirection.SERVER_TO_CLIENT,
            content = response.content(),
            contentEncoding = HttpMapper.contentEncoding(response.headers()),
        )
        capture?.completeBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = body.size.toLong(),
            occurredAtEpochMillis = failedAt,
        )
        responseComplete = true
        lastClientWrite = clientChannel.writeAndFlush(response).addListener { future ->
            capture?.terminate(
                state = if (future.isSuccess) ExchangeState.FAILED else ExchangeState.CANCELLED,
                timings = timingCollector.getTimings(),
                occurredAtEpochMillis = failedAt,
                errorCode = if (future.isSuccess) OUTBOUND_FAILURE else DOWNSTREAM_RESPONSE_REJECTED,
            )
            publishCompletion(false)
            clientChannel.close()
        }
    }

    /** Terminates a response whose upstream channel ended after response delivery began. */
    private fun terminatePartialResponse() {
        if (responseComplete || completionPublished.get()) return
        val failedAt = Clock.System.now().toEpochMilliseconds()
        capture?.cancelBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = responseObservedBytes,
            occurredAtEpochMillis = failedAt,
            errorCode = OUTBOUND_FAILURE,
        )
        capture?.terminate(
            state = ExchangeState.FAILED,
            timings = timingCollector.getTimings(),
            occurredAtEpochMillis = failedAt,
            errorCode = OUTBOUND_FAILURE,
        )
        publishCompletion(false)
        clientChannel.close()
    }

    /** Records completion only after the downstream channel accepts the final response write. */
    private fun terminateAfterDownstreamWrite(
        delivered: Boolean,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
    ) {
        capture?.terminate(
            state = if (delivered) ExchangeState.COMPLETED else ExchangeState.CANCELLED,
            timings = timings,
            occurredAtEpochMillis = occurredAtEpochMillis,
            errorCode = if (delivered) null else DOWNSTREAM_RESPONSE_REJECTED,
        )
    }

    /** Publishes one terminal callback even when write, close, and exception events race. */
    private fun publishCompletion(keepDownstreamAlive: Boolean) {
        if (completionPublished.compareAndSet(false, true)) onExchangeComplete(keepDownstreamAlive)
    }

    private companion object {
        const val OUTBOUND_FAILURE: String = "upstream_response_failed"
        const val DOWNSTREAM_RESPONSE_REJECTED: String = "downstream_response_rejected"
    }
}

/** Copies only bytes admitted by the capture sink and never changes the source buffer indices. */
internal fun captureBodyChunk(
    exchange: ProxyExchangeCapture?,
    direction: TrafficDirection,
    content: ByteBuf,
    contentEncoding: ContentEncoding?,
) {
    if (exchange == null || !content.isReadable) return
    var sourceOffset = 0
    val observedBytes = content.readableBytes()
    while (sourceOffset < observedBytes) {
        val reservation = exchange.tryReserveBody(
            direction = direction,
            contentEncoding = contentEncoding,
            requestedBytes = observedBytes - sourceOffset,
        ) ?: return
        val destination = reservation.writableBytes
        try {
            content.getBytes(content.readerIndex() + sourceOffset, destination)
            sourceOffset += destination.size
            if (!reservation.publish(Clock.System.now().toEpochMilliseconds())) return
        } catch (failure: Throwable) {
            reservation.cancel()
            throw failure
        }
    }
}
