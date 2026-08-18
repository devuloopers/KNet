package com.devuloopers.knet.engine.interceptor

import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointCandidate
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.BreakpointGate
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.HttpResponseSnapshot
import com.devuloopers.knet.traffic.model.body.MessageBodyRef
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse as NettyHttpResponse
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Netty adapter for the application-owned [BreakpointGate].
 *
 * The adapter owns each paused Netty message exactly once, performs no persistence/UI work, and
 * schedules every pipeline mutation back onto the channel event loop.
 */
class KNetInterceptorHandler(
    private val breakpointGate: BreakpointGate,
) : ChannelDuplexHandler() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = mutableMapOf<ExchangeId, Job>()
    private val orderedRequests = ArrayDeque<ProxyRequestContext>()

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        if (msg !is FullHttpRequest || msg.method() == HttpMethod.CONNECT) {
            super.channelRead(context, msg)
            return
        }
        val requestContext = mapRequest(context, msg)
        context.channel().attr(ChannelAttributes.REQUEST_CONTEXT).set(requestContext)
        val candidate = BreakpointCandidate(
            exchangeId = requestContext.exchangeId,
            phase = BreakpointPhase.REQUEST,
            request = requestContext.request,
            requestBody = boundedBody(msg.content().readableBytes()) { copyContent(msg) },
            requestObservedBodyBytes = msg.content().readableBytes().toLong(),
            startedAtEpochMillis = requestContext.startedAtEpochMillis,
        )
        pauseReads(context)
        coordinate(
            context = context,
            candidate = candidate,
            original = msg,
            promise = null,
        ) { decision ->
            when (decision) {
                BreakpointDecision.Drop -> {
                    ReferenceCountUtil.release(msg)
                    context.close()
                }
                BreakpointDecision.ContinueUnchanged -> {
                    orderedRequests.addLast(requestContext)
                    context.fireChannelRead(msg)
                    resumeReads(context)
                }
                is BreakpointDecision.Resume -> {
                    val edit = decision.requestEdit
                    if (edit == null) {
                        orderedRequests.addLast(requestContext)
                        context.fireChannelRead(msg)
                    } else {
                        val updatedContext = requestContext.copy(request = edit.request)
                        context.channel().attr(ChannelAttributes.REQUEST_CONTEXT).set(updatedContext)
                        orderedRequests.addLast(updatedContext)
                        val rebuilt = RequestRebuilder.rebuild(msg, edit)
                        ReferenceCountUtil.release(msg)
                        context.fireChannelRead(rebuilt)
                    }
                    resumeReads(context)
                }
            }
        }
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is FullHttpResponse) {
            val requestContext = orderedRequests.firstOrNull()
            if (requestContext == null) {
                super.write(context, msg, promise)
                return
            }
            val responseSnapshot = HttpResponseSnapshot(
                head = HttpMapper.mapResponseHead(msg),
                body = MessageBodyRef.Empty,
            )
            val candidate = BreakpointCandidate(
                exchangeId = requestContext.exchangeId,
                phase = BreakpointPhase.RESPONSE,
                request = requestContext.request,
                response = responseSnapshot,
                responseBody = boundedBody(msg.content().readableBytes()) { copyContent(msg) },
                responseObservedBodyBytes = msg.content().readableBytes().toLong(),
                startedAtEpochMillis = requestContext.startedAtEpochMillis,
            )
            pauseReads(context)
            coordinate(
                context = context,
                candidate = candidate,
                original = msg,
                promise = promise,
            ) { decision ->
                orderedRequests.removeFirstOrNull()
                when (decision) {
                    BreakpointDecision.Drop -> {
                        ReferenceCountUtil.release(msg)
                        promise.trySuccess()
                        context.close()
                    }
                    BreakpointDecision.ContinueUnchanged -> {
                        context.writeAndFlush(msg, promise)
                        resumeReads(context)
                    }
                    is BreakpointDecision.Resume -> {
                        val edit = decision.responseEdit
                        if (edit == null) {
                            context.writeAndFlush(msg, promise)
                        } else {
                            val rebuilt = ResponseRebuilder.rebuild(msg, edit)
                            ReferenceCountUtil.release(msg)
                            context.writeAndFlush(rebuilt, promise)
                        }
                        resumeReads(context)
                    }
                }
            }
            return
        }
        if (msg is NettyHttpResponse && msg.status().code() >= 200) {
            orderedRequests.removeFirstOrNull()
        }
        super.write(context, msg, promise)
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        cancelActiveWork()
        super.channelInactive(context)
    }

    override fun handlerRemoved(context: ChannelHandlerContext) {
        cancelActiveWork()
        super.handlerRemoved(context)
    }

    private fun coordinate(
        context: ChannelHandlerContext,
        candidate: BreakpointCandidate,
        original: Any,
        promise: ChannelPromise?,
        applyDecision: (BreakpointDecision) -> Unit,
    ) {
        val originalHandled = AtomicBoolean(false)
        val job = scope.launch {
            val decision = try {
                breakpointGate.intercept(candidate)
            } catch (_: CancellationException) {
                throw CancellationException("Breakpoint channel work cancelled.")
            } catch (_: Exception) {
                BreakpointDecision.ContinueUnchanged
            }
            context.executor().execute {
                if (!originalHandled.compareAndSet(false, true)) return@execute
                activeJobs.remove(candidate.exchangeId)
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
        activeJobs[candidate.exchangeId] = job
    }

    private fun mapRequest(context: ChannelHandlerContext, request: FullHttpRequest): ProxyRequestContext {
        val isSsl = context.channel().attr(ChannelAttributes.SSL_ATTR).get() == true ||
            context.pipeline().get(PipelineHandlerNames.SSL) != null
        val authority = resolveAuthority(context, request, isSsl)
        val relativeUri = relativeUri(request.uri())
        return HttpMapper.mapRequestContext(request, isSsl, authority.first, authority.second, relativeUri)
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

    private fun copyContent(response: FullHttpResponse): ByteArray = ByteArray(response.content().readableBytes()).also {
        response.content().getBytes(response.content().readerIndex(), it)
    }

    private fun resolveAuthority(
        context: ChannelHandlerContext,
        request: FullHttpRequest,
        isSsl: Boolean,
    ): Pair<String, Int> {
        val tunneledHost = context.channel().attr(ChannelAttributes.HOST_ATTR).get()
        if (!tunneledHost.isNullOrBlank()) return tunneledHost to if (isSsl) 443 else 80
        val hostHeader = request.headers()["Host"].orEmpty()
        val defaultPort = if (isSsl) 443 else 80
        val bracketEnd = hostHeader.indexOf(']')
        return if (hostHeader.startsWith('[') && bracketEnd > 0) {
            val host = hostHeader.substring(1, bracketEnd)
            host to (hostHeader.substring(bracketEnd + 1).removePrefix(":").toIntOrNull() ?: defaultPort)
        } else {
            val possiblePort = hostHeader.substringAfterLast(':', "").toIntOrNull()
            val host = if (possiblePort == null) hostHeader else hostHeader.substringBeforeLast(':')
            host.ifBlank { "unknown" } to (possiblePort ?: defaultPort)
        }
    }

    private fun relativeUri(uri: String): String = if (uri.startsWith("http://") || uri.startsWith("https://")) {
        runCatching {
            val parsed = URI.create(uri)
            val path = parsed.rawPath?.ifBlank { "/" } ?: "/"
            parsed.rawQuery?.let { "$path?$it" } ?: path
        }.getOrDefault(uri)
    } else {
        uri
    }

    private fun pauseReads(context: ChannelHandlerContext) {
        context.channel().config().isAutoRead = false
    }

    private fun resumeReads(context: ChannelHandlerContext) {
        context.channel().config().isAutoRead = true
        context.read()
    }

    private fun cancelActiveWork() {
        val exchanges = activeJobs.keys.toList()
        exchanges.forEach(breakpointGate::cancelExchange)
        activeJobs.values.forEach(Job::cancel)
        activeJobs.clear()
        orderedRequests.clear()
        scope.cancel()
    }

}

private fun <T> ArrayDeque<T>.removeFirstOrNull(): T? = if (isEmpty()) null else removeFirst()
