package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.contract.breakpoint.BreakpointBody
import com.devuloopers.knet.application.contract.breakpoint.BreakpointCandidate
import com.devuloopers.knet.application.contract.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.BreakpointGate
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.PreparedProxyExchange
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2Error
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import io.netty.handler.codec.http.HttpResponse as NettyHttpResponse

/**
 * Netty adapter for the application-owned [BreakpointGate].
 *
 * The adapter owns each paused Netty message exactly once, performs no persistence/UI work, and
 * schedules every pipeline mutation back onto the channel event loop.
 *
 * @param breakpointGate Application-owned matching, admission, and decision boundary.
 */
class KNetInterceptorHandler(
    private val breakpointGate: BreakpointGate,
) : ChannelDuplexHandler() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = mutableMapOf<ExchangeId, Job>()
    private val orderedRequests = ArrayDeque<ProxyRequestContext>()

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg !is HttpRequest || msg.method() == HttpMethod.CONNECT) {
            super.channelRead(context, msg)
            return
        }
        val requestContext = mapBreakpointRequest(context, msg)
        HttpMapper.removeCaptureAttribution(msg)
        context.channel().attr(ProxyChannelAttributes.REQUEST_CONTEXT).set(requestContext)
        if (msg !is FullHttpRequest) {
            // Keep every forwarded request in the response-order queue. Tracking only requests
            // selected for aggregation would let an earlier unselected response consume the
            // correlation of a later selected pipelined request.
            orderedRequests.addLast(requestContext)
            super.channelRead(context, msg)
            return
        }
        // Capture metadata is admitted before the application forwarding gate can suspend. The
        // forwarding handler consumes this exact handle after resume, so the table can publish one
        // in-progress row without starting a duplicate exchange or coupling capture to UI state.
        val connectionCapture = context.channel().attr(ProxyChannelAttributes.CONNECTION_CAPTURE).get()
        val exchangeCapture = runCatching {
            connectionCapture?.startExchange(
                exchangeId = requestContext.exchangeId,
                request = requestContext.request.head,
                occurredAtEpochMillis = requestContext.startedAtEpochMillis,
                origin = requestContext.origin,
                streamId = context.channel().attr(ProxyChannelAttributes.STREAM_ID).get(),
            )
        }.getOrNull()
        context.channel().attr(ProxyChannelAttributes.PREPARED_EXCHANGE).set(
            PreparedProxyExchange(requestContext.exchangeId, exchangeCapture),
        )
        val readableBodyBytes = msg.content().readableBytes()
        pauseReads(context)
        coordinate(
            context = context,
            exchangeId = requestContext.exchangeId,
            candidateFactory = {
                BreakpointCandidate(
                    exchangeId = requestContext.exchangeId,
                    phase = BreakpointPhase.REQUEST,
                    request = requestContext.request,
                    requestBody = boundedBody(readableBodyBytes) { copyContent(msg) },
                    requestObservedBodyBytes = readableBodyBytes.toLong(),
                    retainedTransportBytes = readableBodyBytes.toLong(),
                    origin = requestContext.origin,
                    startedAtEpochMillis = requestContext.startedAtEpochMillis,
                )
            },
            original = msg,
            promise = null,
        ) { decision ->
            when (decision) {
                BreakpointDecision.Drop -> {
                    breakpointGate.releaseExchange(requestContext.exchangeId)
                    cancelPreparedCapture(
                        context,
                        TrafficTerminationReason.Interception.BREAKPOINT_REQUEST_DROPPED,
                    )
                    ReferenceCountUtil.release(msg)
                    rejectSelectedTransport(context)
                }

                BreakpointDecision.ContinueUnchanged -> {
                    orderedRequests.addLast(requestContext)
                    context.fireChannelRead(msg)
                    resumeReads(context)
                }

                is BreakpointDecision.ResumeRequest -> {
                    val updatedContext = requestContext.copy(request = decision.edit.request)
                    context.channel().attr(ProxyChannelAttributes.REQUEST_CONTEXT).set(updatedContext)
                    orderedRequests.addLast(updatedContext)
                    val rebuilt = RequestRebuilder.rebuild(msg, decision.edit)
                    ReferenceCountUtil.release(msg)
                    context.fireChannelRead(rebuilt)
                    resumeReads(context)
                }

                is BreakpointDecision.ResumeResponse -> {
                    orderedRequests.addLast(requestContext)
                    context.fireChannelRead(msg)
                    resumeReads(context)
                }
            }
        }
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is FullHttpResponse) {
            if (msg.status().code() in 100..199 && msg.status().code() != 101) {
                super.write(context, msg, promise)
                return
            }
            val requestContext = orderedRequests.firstOrNull()
            if (requestContext == null) {
                super.write(context, msg, promise)
                return
            }
            val responseSnapshot = HttpResponseSnapshot(
                head = HttpMapper.mapResponseHead(
                    msg,
                    context.channel().attr(ProxyChannelAttributes.UPSTREAM_APPLICATION_PROTOCOL).get(),
                ),
                body = MessageBodyRef.Empty,
            )
            val readableBodyBytes = msg.content().readableBytes()
            pauseReads(context)
            coordinate(
                context = context,
                exchangeId = requestContext.exchangeId,
                candidateFactory = {
                    BreakpointCandidate(
                        exchangeId = requestContext.exchangeId,
                        phase = BreakpointPhase.RESPONSE,
                        request = requestContext.request,
                        response = responseSnapshot,
                        responseBody = boundedBody(readableBodyBytes) { copyContent(msg) },
                        responseObservedBodyBytes = readableBodyBytes.toLong(),
                        retainedTransportBytes = readableBodyBytes.toLong(),
                        origin = requestContext.origin,
                        startedAtEpochMillis = requestContext.startedAtEpochMillis,
                    )
                },
                original = msg,
                promise = promise,
            ) { decision ->
                orderedRequests.removeFirstOrNull()
                when (decision) {
                    BreakpointDecision.Drop -> {
                        ReferenceCountUtil.release(msg)
                        promise.tryFailure(BreakpointResponseRejectedException())
                        rejectSelectedTransport(context)
                    }

                    BreakpointDecision.ContinueUnchanged -> {
                        context.writeAndFlush(msg, promise)
                        resumeReads(context)
                    }

                    is BreakpointDecision.ResumeResponse -> {
                        val rebuilt = ResponseRebuilder.rebuild(
                            original = msg,
                            edit = decision.edit,
                            requestMethod = HttpMethod.valueOf(requestContext.request.head.method.token),
                        )
                        ReferenceCountUtil.release(msg)
                        context.writeAndFlush(rebuilt, promise)
                        resumeReads(context)
                    }

                    is BreakpointDecision.ResumeRequest -> {
                        context.writeAndFlush(msg, promise)
                        resumeReads(context)
                    }
                }
            }
            return
        }
        if (msg is NettyHttpResponse && (msg.status().code() >= 200 || msg.status().code() == 101)) {
            orderedRequests.removeFirstOrNull()?.let { requestContext ->
                breakpointGate.releaseExchange(requestContext.exchangeId)
            }
        }
        super.write(context, msg, promise)
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        cancelPreparedCapture(
            context,
            TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED_BEFORE_FORWARDING,
        )
        cancelActiveWork()
        super.channelInactive(context)
    }

    override fun handlerRemoved(context: ChannelHandlerContext) {
        cancelPreparedCapture(
            context,
            TrafficTerminationReason.Interception.INTERCEPTOR_REMOVED_BEFORE_FORWARDING,
        )
        cancelActiveWork()
        super.handlerRemoved(context)
    }

    private fun coordinate(
        context: ChannelHandlerContext,
        exchangeId: ExchangeId,
        candidateFactory: () -> BreakpointCandidate,
        original: Any,
        promise: ChannelPromise?,
        applyDecision: (BreakpointDecision) -> Unit,
    ) {
        val originalHandled = AtomicBoolean(false)
        val job = scope.launch {
            val decision = try {
                // The paused message is exclusively owned by this handler, so bounded body copying
                // and protocol inspection can run away from the Netty event loop without mutation.
                breakpointGate.intercept(candidateFactory())
            } catch (_: CancellationException) {
                throw CancellationException("Breakpoint channel work cancelled.")
            } catch (_: Exception) {
                BreakpointDecision.ContinueUnchanged
            }
            context.executor().execute {
                if (!originalHandled.compareAndSet(false, true)) return@execute
                activeJobs.remove(exchangeId)
                if (!context.channel().isActive) {
                    ReferenceCountUtil.release(original)
                    promise?.tryFailure(IllegalStateException("Breakpoint channel closed before decision."))
                } else {
                    applyDecision(decision)
                }
            }
        }
        job.invokeOnCompletion { failure ->
            if (failure != null && originalHandled.compareAndSet(false, true)) {
                ReferenceCountUtil.release(original)
                promise?.tryFailure(failure)
            }
        }
        activeJobs[exchangeId] = job
    }

    private fun boundedBody(readableBytes: Int, copy: () -> ByteArray): BreakpointBody? =
        when {
            readableBytes == 0 -> null
            readableBytes > breakpointGate.requirements.value.maxEditableBodyBytes -> null
            else -> BreakpointBody(copy())
        }

    private fun copyContent(request: FullHttpRequest): ByteArray = ByteArray(request.content().readableBytes()).also {
        request.content().getBytes(request.content().readerIndex(), it)
    }

    private fun copyContent(response: FullHttpResponse): ByteArray =
        ByteArray(response.content().readableBytes()).also {
            response.content().getBytes(response.content().readerIndex(), it)
        }

    private fun pauseReads(context: ChannelHandlerContext) {
        context.channel().config().isAutoRead = false
    }

    private fun resumeReads(context: ChannelHandlerContext) {
        context.channel().config().isAutoRead = true
        context.read()
    }

    /** Rejects one HTTP/2 stream without terminating siblings; HTTP/1 retains connection-close semantics. */
    private fun rejectSelectedTransport(context: ChannelHandlerContext) {
        if (context.channel().attr(ProxyChannelAttributes.STREAM_ID).get() != null) {
            context.writeAndFlush(DefaultHttp2ResetFrame(Http2Error.CANCEL))
                .addListener(ChannelFutureListener.CLOSE)
        } else {
            context.close()
        }
    }

    /** Cancels a capture that has not yet transferred to the forwarding handler. */
    private fun cancelPreparedCapture(
        context: ChannelHandlerContext,
        reason: TrafficTerminationReason,
    ) {
        val prepared = context.channel().attr(ProxyChannelAttributes.PREPARED_EXCHANGE).getAndSet(null) ?: return
        runCatching {
            prepared.capture?.terminate(
                outcome = ExchangeTerminalOutcome.Cancelled(reason),
                timings = ExchangeTimings(),
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    private fun cancelActiveWork() {
        val exchanges = (activeJobs.keys + orderedRequests.map(ProxyRequestContext::exchangeId)).distinct()
        exchanges.forEach(breakpointGate::cancelExchange)
        activeJobs.values.forEach(Job::cancel)
        activeJobs.clear()
        orderedRequests.clear()
        scope.cancel()
    }

}

private class BreakpointResponseRejectedException : java.io.IOException(
    "The intercepted response was dropped before downstream delivery.",
)
