package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.proxy.http.ProxyRequestContext
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.ProxyConnectionAdmissionController
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.dns.NettyDnsResolver
import com.devuloopers.knet.engine.proxy.http.AuthorityParseResult
import com.devuloopers.knet.engine.proxy.http.AuthorityParser
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.pipeline.SelectiveHttpObjectAggregator
import com.devuloopers.knet.engine.proxy.ssl.ProxyTrustManager
import com.devuloopers.knet.engine.proxy.timing.NetworkTimingCollector
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider

private const val STREAMING_TAG = "ProxyEngine"

/**
 * HTTP/1 downstream handler that forwards request heads and content incrementally.
 *
 * The handler owns one active exchange per downstream connection, couples downstream reads to
 * upstream write completion/writability, and retains only a bounded codec batch for pipelined
 * messages that were already decoded when auto-read was paused. Persistence remains a non-blocking
 * side output through [ProxyExchangeCapture].
 */
@Suppress("HttpUrlsUsage")
internal class KNetStreamingProxyHandler(
    private val serverTlsContextProvider: ServerTlsContextProvider,
    private val proxyScope: CoroutineScope,
    private val keyManagerProvider: com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider? = null,
    private val strictSsl: Boolean = true,
    private val runtimePolicy: KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(),
    private val admissionController: ProxyConnectionAdmissionController =
        ProxyConnectionAdmissionController(runtimePolicy),
    private val certificateExecutor: Executor = ForkJoinPool.commonPool(),
    private val connectionCapture: ProxyConnectionCapture? = null,
    private val requiresFullResponseAggregation: (HttpRequestSnapshot) -> Boolean = { false },
) : ChannelInboundHandlerAdapter() {

    companion object {
        private const val MAX_PIPELINED_REQUESTS: Int = 16
        private const val MAX_ALREADY_DECODED_PIPELINE_BYTES: Long = 1L * 1024L * 1024L
        private const val UPSTREAM_LIMIT_ERROR: String = "upstream_connection_limit"
        private const val TLS_HANDSHAKE_ERROR: String = "upstream_tls_handshake_failed"
        private const val UPSTREAM_CONNECT_ERROR: String = "upstream_connect_failed"
        private const val UPSTREAM_WRITE_ERROR: String = "upstream_request_write_failed"
        private const val DOWNSTREAM_CANCELLED: String = "downstream_cancelled"

    }

    private val pendingObjects = ArrayDeque<HttpObject>()
    private var pendingRequestHeads: Int = 0
    private var pendingContentBytes: Long = 0L
    private var activeRequest: ActiveStreamingRequest? = null
    private var discardingConnectContent: Boolean = false

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        val httpObject = message as? HttpObject
        if (httpObject == null) {
            context.fireChannelRead(message)
            return
        }

        if (discardingConnectContent && httpObject is HttpContent) {
            if (httpObject is LastHttpContent) discardingConnectContent = false
            ReferenceCountUtil.release(httpObject)
            return
        }

        handleHttpObject(context, httpObject)
    }

    /** Routes one decoded HTTP object while preserving its reference-counted ownership. */
    private fun handleHttpObject(context: ChannelHandlerContext, message: HttpObject) {
        if (message is FullHttpRequest) {
            if (activeRequest != null) {
                enqueuePipelined(context, message)
            } else {
                handleFullRequest(context, message)
            }
            return
        }

        when (message) {
            is HttpRequest -> {
                if (activeRequest != null) {
                    enqueuePipelined(context, message)
                } else if (message.method() == HttpMethod.CONNECT) {
                    discardingConnectContent = true
                    handleConnect(context, message)
                    ReferenceCountUtil.release(message)
                } else {
                    beginRequest(context, message)
                    ReferenceCountUtil.release(message)
                }
            }

            is HttpContent -> {
                val active = activeRequest
                if (active != null && !active.requestEndReceived) {
                    acceptRequestContent(context, active, message)
                } else {
                    enqueuePipelined(context, message)
                }
            }

            else -> ReferenceCountUtil.release(message)
        }
    }

    /** Splits an already-full breakpoint message into the same streaming ownership path. */
    private fun handleFullRequest(context: ChannelHandlerContext, request: FullHttpRequest) {
        if (request.method() == HttpMethod.CONNECT) {
            handleConnect(context, request)
            ReferenceCountUtil.release(request)
            return
        }
        val head = DefaultHttpRequest(request.protocolVersion(), request.method(), request.uri())
        head.headers().set(request.headers())
        val last = DefaultLastHttpContent(request.content().retainedDuplicate())
        last.trailingHeaders().set(request.trailingHeaders())
        ReferenceCountUtil.release(request)
        beginRequest(context, head)
        if (activeRequest != null) {
            acceptRequestContent(context, activeRequest!!, last)
        } else {
            ReferenceCountUtil.release(last)
        }
    }

    /** Parses the target, publishes request metadata, pauses reads, and starts the upstream dial. */
    private fun beginRequest(context: ChannelHandlerContext, request: HttpRequest) {
        val target = resolveTarget(context, request) ?: return
        val preparedRequest = context.channel().attr(ProxyChannelAttributes.REQUEST_CONTEXT).getAndSet(null)
        val preparedExchange = context.channel().attr(ProxyChannelAttributes.PREPARED_EXCHANGE).getAndSet(null)
        val mappedRequest = preparedRequest ?: HttpMapper.mapRequestContext(
            nettyReq = request,
            isSsl = target.isSsl,
            host = target.host,
            port = target.port,
            relativeUri = target.relativeUri,
        )
        check(preparedExchange == null || preparedExchange.exchangeId == mappedRequest.exchangeId) {
            "Prepared capture identity does not match the streamed request."
        }
        val capture = preparedExchange?.capture ?: connectionCapture?.startExchange(
            exchangeId = mappedRequest.exchangeId,
            request = mappedRequest.request.head,
            occurredAtEpochMillis = mappedRequest.startedAtEpochMillis,
        )
        val outboundHead = DefaultHttpRequest(
            request.protocolVersion(),
            request.method(),
            target.relativeUri,
        )
        outboundHead.headers().set(request.headers())
        outboundHead.headers().remove("Proxy-Connection")
        outboundHead.headers().set(
            HttpHeaderNames.HOST,
            if (target.port == 80 || target.port == 443) target.host else "${target.host}:${target.port}",
        )

        val timings = NetworkTimingCollector().apply { markDnsStart() }
        val active = ActiveStreamingRequest(
            mappedRequest = mappedRequest,
            target = target,
            outboundHead = outboundHead,
            capture = capture,
            contentEncoding = HttpMapper.contentEncoding(request.headers()),
            timings = timings,
        )
        activeRequest = active
        context.channel().config().isAutoRead = false
        connectUpstream(context, active)
    }

    /** Captures and queues one owned content object before pumping it to the upstream channel. */
    private fun acceptRequestContent(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        content: HttpContent,
    ) {
        val readableBytes = content.content().readableBytes()
        active.observedRequestBytes += readableBytes.toLong()
        captureBodyChunk(
            exchange = active.capture,
            direction = TrafficDirection.CLIENT_TO_SERVER,
            content = content.content(),
            contentEncoding = active.contentEncoding,
        )
        active.bodyQueue.addLast(content)
        if (content is LastHttpContent) {
            active.requestEndReceived = true
            active.capture?.completeBody(
                direction = TrafficDirection.CLIENT_TO_SERVER,
                observedBytes = active.observedRequestBytes,
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
        }
        pumpRequestBody(context, active)
    }

    /** Writes one chunk at a time and advances downstream reads only while the origin is writable. */
    private fun pumpRequestBody(context: ChannelHandlerContext, active: ActiveStreamingRequest) {
        if (activeRequest !== active || active.writeInProgress || !active.requestHeadWritten) return
        val upstream = active.upstreamChannel ?: return
        if (!upstream.isActive || !upstream.isWritable) return

        val content = active.bodyQueue.removeFirstOrNull()
        if (content == null) {
            if (!active.requestEndReceived && context.channel().isActive) context.read()
            return
        }

        active.writeInProgress = true
        val wasLast = content is LastHttpContent
        upstream.writeAndFlush(content).addListener { writeFuture ->
            context.executor().execute {
                active.writeInProgress = false
                if (!writeFuture.isSuccess) {
                    failExchange(
                        context = context,
                        active = active,
                        status = HttpResponseStatus.BAD_GATEWAY,
                        errorCode = UPSTREAM_WRITE_ERROR,
                        causeMessage = writeFuture.cause()?.message,
                    )
                    return@execute
                }
                if (wasLast) active.requestEndWritten = true
                pumpRequestBody(context, active)
            }
        }
    }

    /** Resolves DNS away from the event loop, then builds the one-shot upstream channel. */
    private fun connectUpstream(context: ChannelHandlerContext, active: ActiveStreamingRequest) {
        proxyScope.launch {
            val resolvedHost = try {
                InetAddress.getByName(active.target.host).hostAddress.also { active.timings.markDnsEnd() }
            } catch (_: Exception) {
                active.timings.markDnsEnd()
                active.target.host
            }

            context.executor().execute {
                if (activeRequest !== active || !context.channel().isActive) return@execute
                active.timings.markTcpStart()
                val upstreamLease = admissionController.tryAcquireUpstream()
                if (upstreamLease == null) {
                    failExchange(
                        context,
                        active,
                        HttpResponseStatus.SERVICE_UNAVAILABLE,
                        UPSTREAM_LIMIT_ERROR,
                    )
                    return@execute
                }

                val bootstrap = Bootstrap()
                    .group(context.channel().eventLoop())
                    .channel(NioSocketChannel::class.java)
                    .option(ChannelOption.AUTO_READ, false)
                    .option(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        runtimePolicy.connectTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    )
                    .resolver(NettyDnsResolver.resolverGroup)
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(channel: SocketChannel) {
                            configureUpstreamPipeline(context, active, channel)
                        }
                    })

                val connectFuture = bootstrap.connect(resolvedHost, active.target.port)
                connectFuture.addListener { future ->
                    context.executor().execute {
                        active.timings.markTcpEnd()
                        if (future.isSuccess) {
                            active.upstreamChannel = connectFuture.channel()
                            connectFuture.channel().closeFuture().addListener { upstreamLease.close() }
                            pumpRequestBody(context, active)
                        } else {
                            upstreamLease.close()
                            failExchange(
                                context = context,
                                active = active,
                                status = HttpResponseStatus.BAD_GATEWAY,
                                errorCode = UPSTREAM_CONNECT_ERROR,
                                causeMessage = future.cause()?.message,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Installs TLS, codecs, optional response-breakpoint aggregation, and response streaming. */
    private fun configureUpstreamPipeline(
        downstreamContext: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        channel: SocketChannel,
    ) {
        val pipeline = channel.pipeline()
        pipeline.addLast(
            PipelineHandlerNames.READ_TIMEOUT,
            ReadTimeoutHandler(runtimePolicy.readIdleTimeoutMillis, TimeUnit.MILLISECONDS),
        )
        pipeline.addLast(
            PipelineHandlerNames.WRITE_TIMEOUT,
            WriteTimeoutHandler(runtimePolicy.writeIdleTimeoutMillis, TimeUnit.MILLISECONDS),
        )
        if (active.target.isSsl) {
            active.timings.markTlsStart()
            val sslBuilder = SslContextBuilder.forClient()
                .trustManager(ProxyTrustManager.getTrustManagerFactory(strictSsl))
            keyManagerProvider?.getKeyManagerFactory(active.target.host)?.let(sslBuilder::keyManager)
            val sslHandler = sslBuilder.build()
                .newHandler(channel.alloc(), active.target.host, active.target.port)
                .apply { setHandshakeTimeoutMillis(runtimePolicy.tlsHandshakeTimeoutMillis) }
            sslHandler.handshakeFuture().addListener { handshake ->
                downstreamContext.executor().execute {
                    if (handshake.isSuccess) {
                        active.timings.markTlsEnd()
                    } else {
                        failExchange(
                            context = downstreamContext,
                            active = active,
                            status = HttpResponseStatus.BAD_GATEWAY,
                            errorCode = TLS_HANDSHAKE_ERROR,
                            causeMessage = handshake.cause()?.message,
                        )
                    }
                }
            }
            pipeline.addLast(PipelineHandlerNames.SSL, sslHandler)
        }
        pipeline.addLast(PipelineHandlerNames.HTTP_CODEC, HttpClientCodec())
        if (requiresFullResponseAggregation(active.mappedRequest.request)) {
            pipeline.addLast(
                PipelineHandlerNames.HTTP_AGGREGATOR,
                SelectiveHttpObjectAggregator(
                    maximumContentBytes = PipelineHandlerNames.MAX_CONTENT_LENGTH_BYTES,
                    shouldAggregate = { _, _ -> true },
                ),
            )
        }
        pipeline.addLast(
            PipelineHandlerNames.OUTBOUND_HANDLER,
            KNetOutboundHandler(
                clientChannel = downstreamContext.channel(),
                request = active.outboundHead,
                timingCollector = active.timings,
                capture = active.capture,
                onRequestHeadWritten = { upstream ->
                    downstreamContext.executor().execute {
                        if (activeRequest === active) {
                            active.upstreamChannel = upstream
                            active.requestHeadWritten = true
                            pumpRequestBody(downstreamContext, active)
                        }
                    }
                },
                onUpstreamWritable = {
                    downstreamContext.executor().execute {
                        if (activeRequest === active) pumpRequestBody(downstreamContext, active)
                    }
                },
                onExchangeComplete = { completeExchange(downstreamContext, active) },
            ),
        )
    }

    /** Handles CONNECT without blocking certificate generation on the event loop. */
    private fun handleConnect(context: ChannelHandlerContext, request: HttpRequest) {
        val parsedAuthority = AuthorityParser.parse(request.uri(), defaultPort = 443)
        if (parsedAuthority !is AuthorityParseResult.Valid) {
            writeBadRequest(context, "Invalid CONNECT authority")
            return
        }
        val host = parsedAuthority.authority.host
        val port = parsedAuthority.authority.port
        context.channel().attr(ProxyChannelAttributes.HOST).set(host)
        context.channel().attr(ProxyChannelAttributes.PORT).set(port)
        context.channel().attr(ProxyChannelAttributes.IS_SSL).set(true)

        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus(200, "Connection Established"),
        )
        response.headers().set("Proxy-Agent", "KNet")
        context.channel().config().isAutoRead = false
        context.writeAndFlush(response).addListener { writeFuture ->
            if (!writeFuture.isSuccess) {
                context.close()
                return@addListener
            }
            serverTlsContextProvider.resolve(host, certificateExecutor)
                .whenComplete { sslContext, failure ->
                    context.executor().execute {
                        if (failure != null || sslContext == null || !context.channel().isActive) {
                            context.close()
                            return@execute
                        }
                        try {
                            val pipeline = context.pipeline()
                            pipeline.remove(PipelineHandlerNames.HTTP_CODEC)
                            pipeline.get(PipelineHandlerNames.HTTP_AGGREGATOR)?.let { pipeline.remove(it) }
                            val sslHandler = sslContext.newHandler(context.alloc()).apply {
                                setHandshakeTimeoutMillis(runtimePolicy.tlsHandshakeTimeoutMillis)
                            }
                            pipeline.addFirst(PipelineHandlerNames.SSL, sslHandler)
                            pipeline.addAfter(
                                PipelineHandlerNames.SSL,
                                PipelineHandlerNames.HTTP_CODEC,
                                HttpServerCodec(),
                            )
                            context.channel().config().isAutoRead = true
                        } catch (pipelineFailure: Exception) {
                            KNetLogger.error(STREAMING_TAG, pipelineFailure) {
                                "Failed to configure streaming TLS pipeline for $host: ${pipelineFailure.message}"
                            }
                            context.close()
                        }
                    }
                }
        }
    }

    /** Resolves absolute/origin-form request routing without accepting malformed authorities. */
    private fun resolveTarget(context: ChannelHandlerContext, request: HttpRequest): ResolvedTarget? {
        val tunnelSsl = context.channel().attr(ProxyChannelAttributes.IS_SSL).get() ?: false
        var isSsl = tunnelSsl
        var targetHost = context.channel().attr(ProxyChannelAttributes.HOST).get()
        var targetPort = context.channel().attr(ProxyChannelAttributes.PORT).get() ?: if (isSsl) 443 else 80
        val absoluteUri = if (request.uri().startsWith("http://") || request.uri().startsWith("https://")) {
            runCatching { URI.create(request.uri()) }.getOrNull()
        } else {
            null
        }

        if (absoluteUri != null) {
            targetHost = absoluteUri.host
            isSsl = absoluteUri.scheme.equals("https", ignoreCase = true)
            targetPort = when {
                absoluteUri.port != -1 -> absoluteUri.port
                isSsl -> 443
                else -> 80
            }
        } else if (targetHost == null) {
            val hostHeader = request.headers().get(HttpHeaderNames.HOST)
            when (val authority = hostHeader?.let {
                AuthorityParser.parse(it, defaultPort = if (isSsl) 443 else 80)
            }) {
                is AuthorityParseResult.Valid -> {
                    targetHost = authority.authority.host
                    targetPort = authority.authority.port
                }
                else -> {
                    writeBadRequest(context, "Missing or invalid Host authority")
                    return null
                }
            }
        }

        if (targetHost == null) {
            writeBadRequest(context, "Missing target authority")
            return null
        }
        val localPort = (context.channel().localAddress() as? InetSocketAddress)?.port ?: -1
        if (isSelfTarget(targetHost, targetPort, localPort)) {
            writeBadRequest(context, "Recursive self-proxy connection")
            return null
        }

        val relativeUri = if (absoluteUri != null) {
            val path = absoluteUri.rawPath.ifEmpty { "/" }
            absoluteUri.rawQuery?.let { query -> "$path?$query" } ?: path
        } else {
            request.uri()
        }
        return ResolvedTarget(targetHost, targetPort, isSsl, relativeUri)
    }

    /** Queues only objects already decoded after read suspension and rejects an excessive pipeline. */
    private fun enqueuePipelined(context: ChannelHandlerContext, message: HttpObject) {
        val addedHeads = if (message is HttpRequest) 1 else 0
        val addedBytes = (message as? HttpContent)?.content()?.readableBytes()?.toLong() ?: 0L
        if (
            pendingRequestHeads + addedHeads > MAX_PIPELINED_REQUESTS ||
            pendingContentBytes + addedBytes > MAX_ALREADY_DECODED_PIPELINE_BYTES
        ) {
            ReferenceCountUtil.release(message)
            releasePendingObjects()
            val response = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.TOO_MANY_REQUESTS,
            )
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
            context.writeAndFlush(response).addListener { context.close() }
            return
        }
        pendingRequestHeads += addedHeads
        pendingContentBytes += addedBytes
        pendingObjects.addLast(message)
    }

    /** Completes current ownership, then starts the next already-decoded pipelined request in order. */
    private fun completeExchange(context: ChannelHandlerContext, active: ActiveStreamingRequest) {
        if (!context.executor().inEventLoop()) {
            context.executor().execute { completeExchange(context, active) }
            return
        }
        if (!active.exchangeCompleted.compareAndSet(false, true)) return
        if (activeRequest === active) activeRequest = null
        active.upstreamChannel?.close()
        releaseBodyQueue(active)

        if (!active.requestEndReceived || !context.channel().isActive) {
            context.close()
            return
        }
        context.channel().attr(ProxyChannelAttributes.REQUEST_CONTEXT).set(null)
        drainPendingObjects(context)
        if (activeRequest == null && pendingObjects.isEmpty() && context.channel().isActive) {
            context.channel().config().isAutoRead = true
        }
    }

    /** Drains queued objects only through the end of the next request body. */
    private fun drainPendingObjects(context: ChannelHandlerContext) {
        while (pendingObjects.isNotEmpty()) {
            val current = activeRequest
            if (current != null && current.requestEndReceived) return
            val next = pendingObjects.removeFirst()
            if (next is HttpRequest) pendingRequestHeads--
            if (next is HttpContent) pendingContentBytes -= next.content().readableBytes().toLong()
            handleHttpObject(context, next)
            if (!context.channel().isActive) return
        }
    }

    /** Publishes one generated failure response and releases request-side ownership. */
    private fun failExchange(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        status: HttpResponseStatus,
        errorCode: String,
        causeMessage: String? = null,
    ) {
        if (activeRequest !== active || !active.exchangeCompleted.compareAndSet(false, true)) return
        activeRequest = null
        active.upstreamChannel?.close()
        releaseBodyQueue(active)
        if (!active.requestEndReceived) {
            active.capture?.cancelBody(
                direction = TrafficDirection.CLIENT_TO_SERVER,
                observedBytes = active.observedRequestBytes,
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                errorCode = errorCode,
            )
        }

        val bodyBytes = causeMessage
            ?.let { "$status: $it" }
            ?.toByteArray(Charsets.UTF_8)
            ?: ByteArray(0)
        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            Unpooled.wrappedBuffer(bodyBytes),
        )
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bodyBytes.size)
        val now = Clock.System.now().toEpochMilliseconds()
        active.capture?.observeResponse(HttpMapper.mapResponseHead(response), now)
        active.capture?.terminate(
            state = ExchangeState.FAILED,
            timings = active.timings.getTimings(),
            occurredAtEpochMillis = now,
            errorCode = errorCode,
        )
        context.writeAndFlush(response).addListener { context.close() }
    }

    /** Releases every request chunk that has not transferred to an upstream channel. */
    private fun releaseBodyQueue(active: ActiveStreamingRequest) {
        while (active.bodyQueue.isNotEmpty()) {
            ReferenceCountUtil.release(active.bodyQueue.removeFirst())
        }
    }

    /** Releases every already-decoded pipelined object on close or rejection. */
    private fun releasePendingObjects() {
        while (pendingObjects.isNotEmpty()) ReferenceCountUtil.release(pendingObjects.removeFirst())
        pendingRequestHeads = 0
        pendingContentBytes = 0L
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        activeRequest?.let { active ->
            releaseBodyQueue(active)
            active.upstreamChannel?.close()
            if (active.exchangeCompleted.compareAndSet(false, true)) {
                active.capture?.cancelBody(
                    direction = TrafficDirection.CLIENT_TO_SERVER,
                    observedBytes = active.observedRequestBytes,
                    occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    errorCode = DOWNSTREAM_CANCELLED,
                )
                active.capture?.terminate(
                    state = ExchangeState.CANCELLED,
                    timings = active.timings.getTimings(),
                    occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    errorCode = DOWNSTREAM_CANCELLED,
                )
            }
        }
        activeRequest = null
        releasePendingObjects()
        super.channelInactive(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        if (cause is java.io.IOException) {
            KNetLogger.debug(STREAMING_TAG) { "Streaming proxy IO close: ${cause.message}" }
        } else {
            KNetLogger.error(STREAMING_TAG, cause) { "Streaming proxy failure: ${cause.message}" }
        }
        context.close()
    }

    /** Writes a bounded invalid-request response and closes the connection. */
    private fun writeBadRequest(context: ChannelHandlerContext, reason: String) {
        KNetLogger.warn(STREAMING_TAG) { "Rejected streaming proxy request: $reason" }
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
        context.writeAndFlush(response).addListener { context.close() }
    }

    /** Returns whether the request would recursively target KNet's own listener. */
    private fun isSelfTarget(targetHost: String, targetPort: Int, localPort: Int): Boolean {
        val localHost = targetHost == "127.0.0.1" ||
            targetHost == "localhost" ||
            targetHost.equals("knet.local", ignoreCase = true) ||
            isLocalMachineIp(targetHost)
        return localHost && (targetPort == localPort || targetPort == 8080)
    }

    /** Checks the current machine's network interfaces without retaining network state. */
    private fun isLocalMachineIp(host: String): Boolean = try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        var matched = false
        while (!matched && interfaces.hasMoreElements()) {
            val addresses = interfaces.nextElement().inetAddresses
            while (!matched && addresses.hasMoreElements()) matched = addresses.nextElement().hostAddress == host
        }
        matched
    } catch (_: Exception) {
        false
    }

    /** Immutable route resolved from one downstream request head. */
    private data class ResolvedTarget(
        val host: String,
        val port: Int,
        val isSsl: Boolean,
        val relativeUri: String,
    )

    /** Mutable event-loop-confined ownership for one streaming HTTP/1 exchange. */
    private data class ActiveStreamingRequest(
        val mappedRequest: ProxyRequestContext,
        val target: ResolvedTarget,
        val outboundHead: HttpRequest,
        val capture: ProxyExchangeCapture?,
        val contentEncoding: ContentEncoding?,
        val timings: NetworkTimingCollector,
        val bodyQueue: ArrayDeque<HttpContent> = ArrayDeque(),
        val exchangeCompleted: AtomicBoolean = AtomicBoolean(false),
        var upstreamChannel: Channel? = null,
        var requestHeadWritten: Boolean = false,
        var writeInProgress: Boolean = false,
        var requestEndReceived: Boolean = false,
        var requestEndWritten: Boolean = false,
        var observedRequestBytes: Long = 0L,
    )
}
