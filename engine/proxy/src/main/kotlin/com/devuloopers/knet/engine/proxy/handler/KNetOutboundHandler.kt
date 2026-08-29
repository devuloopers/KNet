package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.http.HttpOneDownstreamPolicy
import com.devuloopers.knet.engine.proxy.http.HttpOneSemantics
import com.devuloopers.knet.engine.proxy.http.HttpTwoBridgeHeaders
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.timing.NetworkTimingCollector
import com.devuloopers.knet.engine.proxy.inspection.NettyPayloadSlice
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
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
 * Streams one upstream response to its downstream client with explicit backpressure.
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
 * @param upstreamProtocol Protocol negotiated by a transport adapter when Netty's HTTP-object bridge
 * represents that protocol using HTTP/1-shaped objects.
 * @param onUpgradeAccepted Transfers an accepted HTTP/1.1 protocol switch to the raw duplex relay.
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
    private val upstreamProtocol: ApplicationProtocol? = null,
    private val streamInspectors: List<ProxyStreamInspector> = emptyList(),
    private val streamTransformer: ProxyStreamTransformer? = null,
    private val onUpgradeAccepted: (Channel, com.devuloopers.knet.traffic.model.http.ResponseHead, Long) -> Unit =
        { _, _, _ -> },
) : SimpleChannelInboundHandler<HttpObject>() {
    private val requestStarted = AtomicBoolean(false)
    private var responseStarted: Boolean = false
    private var isKeepAlive: Boolean = true
    private val completionPublished = AtomicBoolean(false)
    private var lastClientWrite: io.netty.channel.ChannelFuture? = null
    private var responseComplete: Boolean = false
    private var provisionalResponseInProgress: Boolean = false
    private var responseObservedBytes: Long = 0L
    private var responseContentEncoding: ContentEncoding? = null
    private val transformQueue = ArrayDeque<TransformInput>()
    private var transformInProgress: Boolean = false

    override fun handlerAdded(context: ChannelHandlerContext) {
        // Happy Eyeballs installs the HTTP pipeline only after a bare TCP socket wins. Netty has
        // already emitted channelActive in that case, so start explicitly from handlerAdded.
        if (context.channel().isActive) startRequest(context)
    }

    override fun channelActive(context: ChannelHandlerContext) {
        startRequest(context)
        context.fireChannelActive()
    }

    private fun startRequest(context: ChannelHandlerContext) {
        if (!requestStarted.compareAndSet(false, true)) return
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
            is HttpResponse -> acceptResponseHead(context, message, retainForWrite = true)
            is HttpContent -> acceptResponseContent(context, message)
        }
    }

    private fun acceptFullResponse(context: ChannelHandlerContext, response: FullHttpResponse) {
        publishUpstreamProtocol(response)
        HttpTwoBridgeHeaders.removeFrom(response.headers())
        HttpTwoBridgeHeaders.removeFrom(response.trailingHeaders())
        if (response.status().code() in 100..199 && response.status().code() != 101) {
            HttpOneSemantics.prepareProvisionalResponse(response, downstreamPolicy)
            KNetLogger.debug(OUTBOUND_TAG) { "KNet Proxy Provisional Full Response: ${response.status()}" }
            lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(response))
            return
        }
        if (response.status() == HttpResponseStatus.SWITCHING_PROTOCOLS) {
            acceptValidatedUpgradeResponse(context, response)
            return
        }
        if (streamTransformer != null) {
            val head = DefaultHttpResponse(response.protocolVersion(), response.status())
            head.headers().set(response.headers())
            acceptResponseHead(context, head, retainForWrite = false)
            enqueueTransformedResponse(
                context = context,
                payload = ByteArray(response.content().readableBytes()).also { bytes ->
                    response.content().getBytes(response.content().readerIndex(), bytes)
                },
                isLast = true,
                trailers = HttpMapper.mapHeaders(response.trailingHeaders()),
            )
            return
        }
        timingCollector.markFirstByteReceived()
        timingCollector.markLastByteReceived()
        responseStarted = true
        val upstreamResponseHead = HttpMapper.mapResponseHead(response, upstreamProtocol)
        isKeepAlive = HttpOneSemantics.prepareFinalResponse(
            response = response,
            requestMethod = request.method(),
            policy = downstreamPolicy,
        )
        KNetLogger.info(OUTBOUND_TAG) { "KNet Proxy Full Response: ${response.status()}" }

        val completedAt = Clock.System.now().toEpochMilliseconds()
        capture?.observeResponse(upstreamResponseHead, completedAt)
        streamInspectors.forEach { inspector -> runCatching { inspector.onResponse(upstreamResponseHead, completedAt) } }
        val trailers = HttpMapper.mapHeaders(response.trailingHeaders())
        if (trailers.isNotEmpty()) {
            streamInspectors.forEach { inspector ->
                runCatching {
                    inspector.onTrailers(TrafficDirection.SERVER_TO_CLIENT, trailers, completedAt)
                }
            }
            capture?.observeTrailers(
                direction = TrafficDirection.SERVER_TO_CLIENT,
                trailers = trailers,
                occurredAtEpochMillis = completedAt,
            )
        }
        captureBodyChunk(
            exchange = capture,
            direction = TrafficDirection.SERVER_TO_CLIENT,
            content = response.content(),
            contentEncoding = HttpMapper.contentEncoding(response.headers()),
        )
        observePayload(response.content(), completedAt)
        capture?.completeBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = response.content().readableBytes().toLong(),
            occurredAtEpochMillis = completedAt,
        )
        observeDirectionEnd(completedAt)
        responseComplete = true
        val timings = timingCollector.getTimings()
        lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(response)).addListener { future ->
            terminateAfterDownstreamWrite(future.isSuccess, timings, completedAt)
            publishCompletion(isKeepAlive)
            if (!isKeepAlive) clientChannel.close()
            context.close()
        }
    }

    private fun acceptResponseHead(
        context: ChannelHandlerContext,
        response: HttpResponse,
        retainForWrite: Boolean,
    ) {
        publishUpstreamProtocol(response)
        HttpTwoBridgeHeaders.removeFrom(response.headers())
        provisionalResponseInProgress = response.status().code() in 100..199 && response.status().code() != 101
        if (provisionalResponseInProgress) {
            HttpOneSemantics.prepareProvisionalResponse(response, downstreamPolicy)
            KNetLogger.debug(OUTBOUND_TAG) { "KNet Proxy Provisional Response Headers: ${response.status()}" }
            lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(response))
            return
        }
        if (response.status() == HttpResponseStatus.SWITCHING_PROTOCOLS) {
            acceptValidatedUpgradeResponse(context, response, retainForWrite)
            return
        }

        timingCollector.markFirstByteReceived()
        responseStarted = true
        responseContentEncoding = HttpMapper.contentEncoding(response.headers())
        if (streamTransformer != null) {
            // A message replacement can change both framing and representation integrity metadata.
            PayloadTransformationHeaders.sanitizeResponse(response.headers())
        }
        val upstreamResponseHead = HttpMapper.mapResponseHead(response, upstreamProtocol)
        isKeepAlive = HttpOneSemantics.prepareFinalResponse(
            response = response,
            requestMethod = request.method(),
            policy = downstreamPolicy,
        )
        val observedAt = Clock.System.now().toEpochMilliseconds()
        capture?.observeResponse(upstreamResponseHead, observedAt)
        streamInspectors.forEach { inspector -> runCatching { inspector.onResponse(upstreamResponseHead, observedAt) } }
        streamTransformer?.onResponse(upstreamResponseHead, observedAt)
        KNetLogger.info(OUTBOUND_TAG) { "KNet Proxy Response Headers: ${response.status()}" }
        lastClientWrite = clientChannel.writeAndFlush(
            if (retainForWrite) ReferenceCountUtil.retain(response) else response,
        )
    }

    /** Validates both HTTP/1.1 handshake legs before transferring raw transport ownership. */
    private fun acceptValidatedUpgradeResponse(
        context: ChannelHandlerContext,
        response: HttpResponse,
        retainForWrite: Boolean = true,
    ) {
        if (!isAcceptedUpgrade(response)) {
            exceptionCaught(context, IOException("Upstream returned an invalid HTTP Upgrade response."))
            return
        }
        timingCollector.markFirstByteReceived()
        timingCollector.markLastByteReceived()
        responseStarted = true
        responseComplete = true
        val observedAt = Clock.System.now().toEpochMilliseconds()
        val responseHead = HttpMapper.mapResponseHead(response, upstreamProtocol)
        capture?.observeResponse(responseHead, observedAt)
        capture?.completeBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = 0L,
            occurredAtEpochMillis = observedAt,
        )
        streamInspectors.forEach { inspector -> runCatching { inspector.onResponse(responseHead, observedAt) } }
        val write = clientChannel.writeAndFlush(
            if (retainForWrite) ReferenceCountUtil.retain(response) else response,
        )
        lastClientWrite = write
        write.addListener { future ->
            if (future.isSuccess) {
                onUpgradeAccepted(context.channel(), responseHead, observedAt)
            } else {
                capture?.terminate(
                    outcome = ExchangeTerminalOutcome.Cancelled(
                        TrafficTerminationReason.Transport.DOWNSTREAM_RESPONSE_REJECTED,
                    ),
                    timings = timingCollector.getTimings(),
                    occurredAtEpochMillis = observedAt,
                )
                notifyTermination(
                    ExchangeTerminalOutcome.Cancelled(
                        TrafficTerminationReason.Transport.DOWNSTREAM_RESPONSE_REJECTED,
                    ),
                    observedAt,
                )
                publishCompletion(false)
                clientChannel.close()
                context.close()
            }
        }
    }

    /** Matches a switching response to the exact protocol token requested by the downstream client. */
    private fun isAcceptedUpgrade(response: HttpResponse): Boolean {
        if (request.protocolVersion() != io.netty.handler.codec.http.HttpVersion.HTTP_1_1) return false
        if (response.protocolVersion() != io.netty.handler.codec.http.HttpVersion.HTTP_1_1) return false
        val acceptedProtocol = response.headers().tokens(HttpHeaderNames.UPGRADE).singleOrNull() ?: return false
        return request.headers().containsToken(HttpHeaderNames.UPGRADE, acceptedProtocol) &&
            request.headers().containsToken(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE.toString()) &&
            response.headers().containsToken(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE.toString())
    }

    private fun acceptResponseContent(context: ChannelHandlerContext, content: HttpContent) {
        if (provisionalResponseInProgress) {
            if (content is LastHttpContent) HttpOneSemantics.prepareFinalContent(content, downstreamPolicy)
            lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(content))
            if (content is LastHttpContent) provisionalResponseInProgress = false
            return
        }
        val transformer = streamTransformer
        if (transformer != null) {
            val payload = ByteArray(content.content().readableBytes())
            content.content().getBytes(content.content().readerIndex(), payload)
            enqueueTransformedResponse(
                context = context,
                payload = payload,
                isLast = content is LastHttpContent,
                trailers = if (content is LastHttpContent) {
                    HttpMapper.mapHeaders(content.trailingHeaders())
                } else {
                    emptyList()
                },
            )
            return
        }
        acceptPreparedResponseContent(context, content, retainForWrite = true)
    }

    /** Queues a heap-owned response slice and pauses upstream reads until its decision completes. */
    private fun enqueueTransformedResponse(
        context: ChannelHandlerContext,
        payload: ByteArray,
        isLast: Boolean,
        trailers: List<com.devuloopers.knet.traffic.model.http.HeaderField>,
    ) {
        transformQueue.addLast(TransformInput(payload, isLast, trailers))
        processNextResponseTransform(context)
    }

    private fun processNextResponseTransform(context: ChannelHandlerContext) {
        if (transformInProgress || responseComplete) return
        val input = transformQueue.removeFirstOrNull() ?: run {
            if (context.channel().isActive) context.read()
            return
        }
        val transformer = streamTransformer ?: return
        transformInProgress = true
        val now = Clock.System.now().toEpochMilliseconds()
        if (input.trailers.isNotEmpty()) {
            transformer.onTrailers(TrafficDirection.SERVER_TO_CLIENT, input.trailers, now)
        }
        transformer.transform(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            payload = input.payload,
            endOfDirection = input.isLast,
            occurredAtEpochMillis = now,
        ).whenComplete { result, failure ->
            context.executor().execute {
                transformInProgress = false
                if (responseComplete) return@execute
                if (failure != null || result is ProxyStreamTransformResult.DropStream) {
                    terminateTransformedResponse(
                        context,
                        (result as? ProxyStreamTransformResult.DropStream)?.reason
                            ?: TrafficTerminationReason.Interception.PROTOCOL_STREAM_TRANSFORM_FAILED,
                    )
                    return@execute
                }
                val forwarded = (result as ProxyStreamTransformResult.Forward).payload
                if (input.isLast || forwarded.isNotEmpty()) {
                    val prepared = if (input.isLast) {
                        DefaultLastHttpContent(Unpooled.wrappedBuffer(forwarded)).also { last ->
                            input.trailers.forEach { header ->
                                last.trailingHeaders().add(header.name.value, header.value)
                            }
                        }
                    } else {
                        DefaultHttpContent(Unpooled.wrappedBuffer(forwarded))
                    }
                    acceptPreparedResponseContent(context, prepared, retainForWrite = false)
                }
                if (!responseComplete) advanceTransformedResponse(context)
            }
        }
    }

    /** Preserves the ordinary downstream-write-to-upstream-read backpressure contract. */
    private fun advanceTransformedResponse(context: ChannelHandlerContext) {
        val downstreamWrite = lastClientWrite
        lastClientWrite = null
        if (downstreamWrite == null) {
            processNextResponseTransform(context)
            return
        }
        downstreamWrite.addListener { future ->
            context.executor().execute {
                if (future.isSuccess && context.channel().isActive && !responseComplete) {
                    processNextResponseTransform(context)
                } else if (!future.isSuccess) {
                    terminateTransformedResponse(
                        context,
                        TrafficTerminationReason.Transport.DOWNSTREAM_RESPONSE_REJECTED,
                    )
                }
            }
        }
    }

    /** Captures and forwards one post-transform response object. */
    private fun acceptPreparedResponseContent(
        context: ChannelHandlerContext,
        content: HttpContent,
        retainForWrite: Boolean,
    ) {

        val readableBytes = content.content().readableBytes()
        responseObservedBytes += readableBytes.toLong()
        captureBodyChunk(
            exchange = capture,
            direction = TrafficDirection.SERVER_TO_CLIENT,
            content = content.content(),
            contentEncoding = responseContentEncoding,
        )
        observePayload(content.content(), Clock.System.now().toEpochMilliseconds())
        if (content is LastHttpContent) {
            val trailers = HttpMapper.mapHeaders(content.trailingHeaders())
            if (trailers.isNotEmpty()) {
                streamInspectors.forEach { inspector ->
                    runCatching {
                        inspector.onTrailers(
                            TrafficDirection.SERVER_TO_CLIENT,
                            trailers,
                            Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                }
                capture?.observeTrailers(
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    trailers = trailers,
                    occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                )
            }
            HttpOneSemantics.prepareFinalContent(content, downstreamPolicy)
        }
        val clientWrite = clientChannel.writeAndFlush(
            if (retainForWrite) ReferenceCountUtil.retain(content) else content,
        )
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
        observeDirectionEnd(completedAt)
        responseComplete = true
        clientWrite.addListener { future ->
            terminateAfterDownstreamWrite(future.isSuccess, timings, completedAt)
            publishCompletion(isKeepAlive)
            if (!isKeepAlive) clientChannel.close()
            // This closes the exchange channel. For HTTP/1 it is the dedicated upstream socket;
            // for HTTP/2 it is only the leased stream child, leaving the pooled parent reusable.
            context.close()
        }
    }

    /** Advances upstream reads only after the latest downstream write in this decoded batch. */
    override fun channelReadComplete(context: ChannelHandlerContext) {
        val downstreamWrite = lastClientWrite
        lastClientWrite = null
        if (!responseComplete) {
            if (transformInProgress || transformQueue.isNotEmpty()) {
                super.channelReadComplete(context)
                return
            }
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
        streamTransformer?.cancel(TrafficTerminationReason.Transport.UPSTREAM_RESPONSE_FAILED)

        if (!responseStarted) {
            sendSyntheticBadGateway("Outbound Proxy Exception: ${cause.message ?: cause::class.simpleName}")
        } else if (!responseComplete) {
            terminatePartialResponse()
        }
        context.close()
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        streamTransformer?.cancel(TrafficTerminationReason.Transport.UPSTREAM_RESPONSE_FAILED)
        transformQueue.clear()
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
            val outcome = if (future.isSuccess) {
                ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.UPSTREAM_RESPONSE_FAILED)
            } else {
                ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DOWNSTREAM_RESPONSE_REJECTED)
            }
            capture?.terminate(
                outcome = outcome,
                timings = timingCollector.getTimings(),
                occurredAtEpochMillis = failedAt,
            )
            notifyTermination(outcome, failedAt)
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
            reason = TrafficTerminationReason.Transport.UPSTREAM_RESPONSE_FAILED,
        )
        capture?.terminate(
            outcome = ExchangeTerminalOutcome.Failed(
                TrafficTerminationReason.Transport.UPSTREAM_RESPONSE_FAILED,
            ),
            timings = timingCollector.getTimings(),
            occurredAtEpochMillis = failedAt,
        )
        notifyTermination(
            ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.UPSTREAM_RESPONSE_FAILED),
            failedAt,
        )
        publishCompletion(false)
        clientChannel.close()
    }

    /** Terminates only the active exchange/HTTP/2 child stream after a protocol decision drops it. */
    private fun terminateTransformedResponse(
        context: ChannelHandlerContext,
        reason: TrafficTerminationReason,
    ) {
        if (responseComplete || completionPublished.get()) return
        val failedAt = Clock.System.now().toEpochMilliseconds()
        responseComplete = true
        transformQueue.clear()
        streamTransformer?.cancel(reason)
        capture?.cancelBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = responseObservedBytes,
            occurredAtEpochMillis = failedAt,
            reason = reason,
        )
        capture?.terminate(
            outcome = ExchangeTerminalOutcome.Cancelled(reason),
            timings = timingCollector.getTimings(),
            occurredAtEpochMillis = failedAt,
        )
        notifyTermination(ExchangeTerminalOutcome.Cancelled(reason), failedAt)
        publishCompletion(false)
        clientChannel.close()
        context.close()
    }

    /** Records completion only after the downstream channel accepts the final response write. */
    private fun terminateAfterDownstreamWrite(
        delivered: Boolean,
        timings: ExchangeTimings,
        occurredAtEpochMillis: Long,
    ) {
        val outcome = if (delivered) {
            ExchangeTerminalOutcome.Completed
        } else {
            ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DOWNSTREAM_RESPONSE_REJECTED)
        }
        capture?.terminate(
            outcome = outcome,
            timings = timings,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        notifyTermination(outcome, occurredAtEpochMillis)
    }

    /** Delivers a borrowed response slice to additive protocol inspectors. */
    private fun observePayload(content: ByteBuf, occurredAtEpochMillis: Long) {
        if (!content.isReadable) return
        val payload = NettyPayloadSlice(content)
        streamInspectors.forEach { inspector ->
            runCatching {
                inspector.onPayload(TrafficDirection.SERVER_TO_CLIENT, payload, occurredAtEpochMillis)
            }
        }
    }

    private fun observeDirectionEnd(occurredAtEpochMillis: Long) {
        streamInspectors.forEach { inspector ->
            runCatching { inspector.onDirectionEnd(TrafficDirection.SERVER_TO_CLIENT, occurredAtEpochMillis) }
        }
    }

    private fun notifyTermination(
        outcome: ExchangeTerminalOutcome,
        occurredAtEpochMillis: Long,
    ) {
        streamInspectors.forEach { inspector ->
            runCatching { inspector.onExchangeTerminated(outcome, occurredAtEpochMillis) }
        }
    }

    /** Publishes one terminal callback even when write, close, and exception events race. */
    private fun publishCompletion(keepDownstreamAlive: Boolean) {
        if (completionPublished.compareAndSet(false, true)) onExchangeComplete(keepDownstreamAlive)
    }

    /** Makes the true upstream leg visible to an optional downstream breakpoint adapter. */
    private fun publishUpstreamProtocol(response: HttpResponse) {
        clientChannel.attr(ProxyChannelAttributes.UPSTREAM_APPLICATION_PROTOCOL).set(
            upstreamProtocol ?: ApplicationProtocol.fromToken(response.protocolVersion().text()),
        )
    }

    private companion object {
    }

    private data class TransformInput(
        val payload: ByteArray,
        val isLast: Boolean,
        val trailers: List<com.devuloopers.knet.traffic.model.http.HeaderField>,
    )
}

/** Returns whether any repeated/comma-separated header field contains the requested token. */
private fun io.netty.handler.codec.http.HttpHeaders.containsToken(
    name: CharSequence,
    expected: String,
): Boolean = tokens(name).any { token -> token.equals(expected, ignoreCase = true) }

/** Parses repeated HTTP fields using the standard comma-separated token grammar. */
private fun io.netty.handler.codec.http.HttpHeaders.tokens(name: CharSequence): Sequence<String> = getAll(name)
    .asSequence()
    .flatMap { value -> value.split(',').asSequence() }
    .map(String::trim)
    .filter(String::isNotEmpty)

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
