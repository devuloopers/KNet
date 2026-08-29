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
import com.devuloopers.knet.engine.proxy.http.HttpOneDownstreamPolicy
import com.devuloopers.knet.engine.proxy.http.HttpOneRequestViolation
import com.devuloopers.knet.engine.proxy.http.HttpOneSemantics
import com.devuloopers.knet.engine.proxy.http.HttpTwoBridgeHeaders
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.pipeline.SelectiveHttpObjectAggregator
import com.devuloopers.knet.engine.proxy.ssl.ProxyTrustManager
import com.devuloopers.knet.engine.proxy.timing.NetworkTimingCollector
import com.devuloopers.knet.engine.proxy.tls.SniTlsContextHandlerFactory
import com.devuloopers.knet.engine.proxy.upstream.HttpTwoNegotiationUnavailableException
import com.devuloopers.knet.engine.proxy.upstream.HttpTwoUpstreamConnectionPool
import com.devuloopers.knet.engine.proxy.upstream.HttpTwoUpstreamRoute
import com.devuloopers.knet.engine.proxy.inspection.NettyPayloadSlice
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamInspectorFactory
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformerFactory
import com.devuloopers.knet.engine.proxy.inspection.ProxyStreamTransformResult
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexInspector
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexInspectorFactory
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformerFactory
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
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
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerUpgradeHandler
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
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
import java.util.concurrent.CompletionException
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
    private val streamId: StreamId? = null,
    private val downstreamProtocol: ApplicationProtocol? = null,
    private val httpTwoUpstreamPool: HttpTwoUpstreamConnectionPool? = null,
    private val installTlsApplicationProtocol: ((io.netty.channel.ChannelPipeline) -> Unit)? = null,
    private val streamInspectorFactories: List<ProxyStreamInspectorFactory> = emptyList(),
    private val streamTransformerFactories: List<ProxyStreamTransformerFactory> = emptyList(),
    private val duplexInspectorFactories: List<ProxyDuplexInspectorFactory> = emptyList(),
    private val duplexTransformerFactories: List<ProxyDuplexTransformerFactory> = emptyList(),
) : ChannelInboundHandlerAdapter() {

    companion object {
        private const val MAX_PIPELINED_REQUESTS: Int = 16
        private const val MAX_ALREADY_DECODED_PIPELINE_BYTES: Long = 1L * 1024L * 1024L
    }

    private val pendingObjects = ArrayDeque<HttpObject>()
    private val sniTlsContextHandlerFactory = SniTlsContextHandlerFactory(
        tlsContextProvider = serverTlsContextProvider,
        certificateExecutor = certificateExecutor,
        maximumClientHelloBytes = runtimePolicy.maximumTlsClientHelloBytes,
        handshakeTimeoutMillis = runtimePolicy.tlsHandshakeTimeoutMillis,
    )
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
        val downstreamPolicy = HttpOneSemantics.downstreamPolicy(request)
        when (HttpOneSemantics.validateRequest(request)) {
            HttpOneRequestViolation.HTTP_1_0_TRANSFER_ENCODING -> {
                writeBadRequest(
                    context = context,
                    reason = "HTTP/1.0 does not support Transfer-Encoding",
                    requestVersion = request.protocolVersion(),
                )
                return
            }

            null -> Unit
        }
        val target = resolveTarget(context, request) ?: return
        val preparedRequest = context.channel().attr(ProxyChannelAttributes.REQUEST_CONTEXT).getAndSet(null)
        val preparedExchange = context.channel().attr(ProxyChannelAttributes.PREPARED_EXCHANGE).getAndSet(null)
        val mappedRequest = preparedRequest ?: HttpMapper.mapRequestContext(
            nettyReq = request,
            isSsl = target.isSsl,
            host = target.authorityHost,
            port = target.port,
            relativeUri = target.relativeUri,
            protocolOverride = downstreamProtocol,
        )
        HttpMapper.removeCaptureAttribution(request)
        check(preparedExchange == null || preparedExchange.exchangeId == mappedRequest.exchangeId) {
            "Prepared capture identity does not match the streamed request."
        }
        val capture = preparedExchange?.capture ?: connectionCapture?.startExchange(
            exchangeId = mappedRequest.exchangeId,
            request = mappedRequest.request.head,
            occurredAtEpochMillis = mappedRequest.startedAtEpochMillis,
            origin = mappedRequest.origin,
            streamId = streamId,
        )
        val streamInspectors = streamInspectorFactories.mapNotNull { factory ->
            runCatching { factory.create(mappedRequest.request.head, streamId, capture) }
                .onFailure { failure ->
                    KNetLogger.warn(STREAMING_TAG) {
                        "Protocol stream inspector rejected request setup: ${failure::class.simpleName}"
                    }
                }
                .getOrNull()
        }
        val streamTransformer = streamTransformerFactories.firstNotNullOfOrNull { factory ->
            runCatching { factory.create(mappedRequest.request, streamId, capture) }
                .onFailure { failure ->
                    KNetLogger.warn(STREAMING_TAG) {
                        "Protocol stream transformer rejected request setup: ${failure::class.simpleName}"
                    }
                }
                .getOrNull()
        }
        val duplexInspectors = duplexInspectorFactories.mapNotNull { factory ->
            runCatching { factory.create(mappedRequest.request, streamId, capture) }
                .onFailure { failure ->
                    KNetLogger.warn(STREAMING_TAG) {
                        "Duplex inspector rejected request setup: ${failure::class.simpleName}"
                    }
                }
                .getOrNull()
        }
        val duplexTransformer = duplexTransformerFactories.firstNotNullOfOrNull { factory ->
            runCatching { factory.create(mappedRequest.request, streamId, capture) }
                .onFailure { failure ->
                    KNetLogger.warn(STREAMING_TAG) {
                        "Duplex transformer rejected request setup: ${failure::class.simpleName}"
                    }
                }
                .getOrNull()
        }
        val outboundHead = DefaultHttpRequest(
            request.protocolVersion(),
            request.method(),
            target.relativeUri,
        )
        outboundHead.headers().set(request.headers())
        // Downstream HTTP/2 bridge fields are transport metadata. Remove them before either the
        // HTTP/1 upstream wire or the independently allocated upstream HTTP/2 stream sees them.
        HttpTwoBridgeHeaders.removeFrom(outboundHead.headers())
        HttpOneSemantics.prepareUpstreamRequest(outboundHead, downstreamPolicy)
        outboundHead.headers().set(
            HttpHeaderNames.HOST,
            if (target.port == 80 || target.port == 443) {
                target.authorityHost
            } else {
                "${target.authorityHost}:${target.port}"
            },
        )
        if (streamTransformer != null) {
            // A message edit may change framing and representation integrity metadata.
            PayloadTransformationHeaders.sanitizeRequest(outboundHead.headers())
        }

        val timings = NetworkTimingCollector().apply { markDnsStart() }
        val active = ActiveStreamingRequest(
            mappedRequest = mappedRequest,
            target = target,
            outboundHead = outboundHead,
            downstreamPolicy = downstreamPolicy,
            capture = capture,
            streamInspectors = streamInspectors,
            streamTransformer = streamTransformer,
            duplexInspectors = duplexInspectors,
            duplexTransformer = duplexTransformer,
            contentEncoding = HttpMapper.contentEncoding(request.headers()),
            timings = timings,
            requestsDuplexUpgrade = isHttpOneUpgradeRequest(request),
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
        val transformer = active.streamTransformer
        if (transformer != null) {
            val payload = ByteArray(content.content().readableBytes())
            content.content().getBytes(content.content().readerIndex(), payload)
            val isLast = content is LastHttpContent
            val trailers = if (content is LastHttpContent) {
                HttpMapper.mapHeaders(content.trailingHeaders())
            } else {
                emptyList()
            }
            ReferenceCountUtil.release(content)
            active.transformQueue.addLast(TransformInput(payload, isLast, trailers))
            processNextRequestTransform(context, active)
            return
        }
        acceptPreparedRequestContent(context, active, content)
    }

    /** Applies an asynchronous protocol transform while preserving downstream read backpressure. */
    private fun processNextRequestTransform(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
    ) {
        if (activeRequest !== active || active.transformInProgress) return
        val input = active.transformQueue.removeFirstOrNull() ?: run {
            pumpRequestBody(context, active)
            return
        }
        val transformer = active.streamTransformer ?: return
        active.transformInProgress = true
        val now = Clock.System.now().toEpochMilliseconds()
        if (input.trailers.isNotEmpty()) {
            transformer.onTrailers(TrafficDirection.CLIENT_TO_SERVER, input.trailers, now)
        }
        transformer.transform(
            direction = TrafficDirection.CLIENT_TO_SERVER,
            payload = input.payload,
            endOfDirection = input.isLast,
            occurredAtEpochMillis = now,
        ).whenComplete { result, failure ->
            context.executor().execute {
                active.transformInProgress = false
                if (activeRequest !== active) return@execute
                if (failure != null || result is ProxyStreamTransformResult.DropStream) {
                    failExchange(
                        context = context,
                        active = active,
                        status = HttpResponseStatus.BAD_GATEWAY,
                        reason = (result as? ProxyStreamTransformResult.DropStream)?.reason
                            ?: TrafficTerminationReason.Interception.PROTOCOL_STREAM_TRANSFORM_FAILED,
                        causeMessage = failure?.message,
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
                    acceptPreparedRequestContent(context, active, prepared)
                }
                processNextRequestTransform(context, active)
            }
        }
    }

    /** Captures and queues one post-transform content object owned by this handler. */
    private fun acceptPreparedRequestContent(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        content: HttpContent,
    ) {
        val readableBytes = content.content().readableBytes()
        active.observedRequestBytes += readableBytes.toLong()
        if (readableBytes > 0) {
            val payload = NettyPayloadSlice(content.content())
            active.streamInspectors.forEach { inspector ->
                runCatching {
                    inspector.onPayload(
                        direction = TrafficDirection.CLIENT_TO_SERVER,
                        payload = payload,
                        occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    )
                }
            }
        }
        captureBodyChunk(
            exchange = active.capture,
            direction = TrafficDirection.CLIENT_TO_SERVER,
            content = content.content(),
            contentEncoding = active.contentEncoding,
        )
        active.bodyQueue.addLast(content)
        if (content is LastHttpContent) {
            active.requestEndReceived = true
            val trailers = HttpMapper.mapHeaders(content.trailingHeaders())
            if (trailers.isNotEmpty()) {
                active.streamInspectors.forEach { inspector ->
                    runCatching {
                        inspector.onTrailers(
                            direction = TrafficDirection.CLIENT_TO_SERVER,
                            trailers = trailers,
                            occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                }
                active.capture?.observeTrailers(
                    direction = TrafficDirection.CLIENT_TO_SERVER,
                    trailers = trailers,
                    occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                )
            }
            active.capture?.completeBody(
                direction = TrafficDirection.CLIENT_TO_SERVER,
                observedBytes = active.observedRequestBytes,
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
            active.streamInspectors.forEach { inspector ->
                runCatching {
                    inspector.onDirectionEnd(
                        TrafficDirection.CLIENT_TO_SERVER,
                        Clock.System.now().toEpochMilliseconds(),
                    )
                }
            }
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
                        reason = TrafficTerminationReason.Transport.UPSTREAM_REQUEST_WRITE_FAILED,
                        causeMessage = writeFuture.cause()?.message,
                    )
                    return@execute
                }
                if (wasLast) active.requestEndWritten = true
                pumpRequestBody(context, active)
            }
        }
    }

    /** Resolves DNS away from the event loop, then selects pooled HTTP/2 or one-shot HTTP/1. */
    private fun connectUpstream(context: ChannelHandlerContext, active: ActiveStreamingRequest) {
        proxyScope.launch {
            val resolvedHost = try {
                InetAddress.getByName(active.target.routeHost).hostAddress.also { active.timings.markDnsEnd() }
            } catch (_: Exception) {
                active.timings.markDnsEnd()
                active.target.routeHost
            }

            context.executor().execute {
                if (activeRequest !== active || !context.channel().isActive) return@execute
                if (active.target.isSsl && httpTwoUpstreamPool != null && !active.requestsDuplexUpgrade) {
                    connectHttpTwo(context, active, resolvedHost)
                } else {
                    connectHttpOne(context, active, resolvedHost)
                }
            }
        }
    }

    /** Opens an isolated stream on a pooled TLS HTTP/2 parent. */
    private fun connectHttpTwo(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        resolvedHost: String,
    ) {
        val pool = checkNotNull(httpTwoUpstreamPool)
        active.timings.markTcpStart()
        active.timings.markTlsStart()
        val httpTwoRequest = createHttpTwoUpstreamRequest(active.outboundHead)
        pool.openStream(
            eventLoop = context.channel().eventLoop(),
            route = HttpTwoUpstreamRoute(
                host = active.target.tlsServerName,
                resolvedHost = resolvedHost,
                port = active.target.port,
            ),
            streamInitializer = object : ChannelInitializer<Channel>() {
                override fun initChannel(channel: Channel) {
                    channel.pipeline().addLast(
                        PipelineHandlerNames.READ_TIMEOUT,
                        ReadTimeoutHandler(runtimePolicy.readIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                    )
                    channel.pipeline().addLast(
                        PipelineHandlerNames.WRITE_TIMEOUT,
                        WriteTimeoutHandler(runtimePolicy.writeIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                    )
                    channel.pipeline().addLast(
                        PipelineHandlerNames.HTTP2_STREAM_CODEC,
                        Http2StreamFrameToHttpObjectCodec(false),
                    )
                    configureUpstreamResponsePipeline(
                        downstreamContext = context,
                        active = active,
                        channel = channel,
                        request = httpTwoRequest,
                        upstreamProtocol = ApplicationProtocol.fromToken("HTTP/2"),
                    )
                }
            },
        ).whenComplete { stream, failure ->
            context.executor().execute {
                if (activeRequest !== active || !context.channel().isActive) {
                    stream?.close()
                    return@execute
                }
                active.timings.markTcpEnd()
                active.timings.markTlsEnd()
                if (failure == null && stream != null) {
                    active.upstreamChannel = stream
                    pumpRequestBody(context, active)
                    return@execute
                }

                val cause = unwrapCompletionFailure(failure)
                if (cause is HttpTwoNegotiationUnavailableException) {
                    connectHttpOne(context, active, resolvedHost)
                } else {
                    failExchange(
                        context = context,
                        active = active,
                        status = HttpResponseStatus.BAD_GATEWAY,
                        reason = TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                        causeMessage = cause?.message,
                    )
                }
            }
        }
    }

    /** Creates the existing one-exchange HTTP/1 upstream channel. */
    private fun connectHttpOne(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        resolvedHost: String,
    ) {
        active.timings.markTcpStart()
        val upstreamLease = admissionController.tryAcquireUpstream()
        if (upstreamLease == null) {
            failExchange(
                context,
                active,
                HttpResponseStatus.SERVICE_UNAVAILABLE,
                TrafficTerminationReason.Transport.UPSTREAM_CONNECTION_LIMIT,
            )
            return
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
                    configureHttpOneUpstreamPipeline(context, active, channel)
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
                        reason = TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                        causeMessage = future.cause()?.message,
                    )
                }
            }
        }
    }

    /** Installs TLS, codecs, optional response-breakpoint aggregation, and response streaming. */
    private fun configureHttpOneUpstreamPipeline(
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
            keyManagerProvider?.getKeyManagerFactory(active.target.tlsServerName)?.let(sslBuilder::keyManager)
            val sslHandler = sslBuilder.build()
                .newHandler(channel.alloc(), active.target.tlsServerName, active.target.port)
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
                            reason = TrafficTerminationReason.Transport.UPSTREAM_TLS_HANDSHAKE_FAILED,
                            causeMessage = handshake.cause()?.message,
                        )
                    }
                }
            }
            pipeline.addLast(PipelineHandlerNames.SSL, sslHandler)
        }
        pipeline.addLast(PipelineHandlerNames.HTTP_CODEC, HttpClientCodec())
        configureUpstreamResponsePipeline(
            downstreamContext = downstreamContext,
            active = active,
            channel = channel,
            request = active.outboundHead,
            upstreamProtocol = null,
        )
    }

    /** Installs response aggregation/capture after either the HTTP/1 or HTTP/2 object bridge. */
    private fun configureUpstreamResponsePipeline(
        downstreamContext: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        channel: Channel,
        request: HttpRequest,
        upstreamProtocol: ApplicationProtocol?,
    ) {
        val pipeline = channel.pipeline()
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
                request = request,
                timingCollector = active.timings,
                capture = active.capture,
                streamInspectors = active.streamInspectors,
                streamTransformer = active.streamTransformer,
                downstreamPolicy = active.downstreamPolicy,
                upstreamProtocol = upstreamProtocol,
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
                onExchangeComplete = { keepDownstreamAlive ->
                    completeExchange(downstreamContext, active, keepDownstreamAlive)
                },
                onUpgradeAccepted = { upstream, response, occurredAtEpochMillis ->
                    establishDuplexRelay(
                        downstreamContext = downstreamContext,
                        active = active,
                        upstreamChannel = upstream,
                        response = response,
                        occurredAtEpochMillis = occurredAtEpochMillis,
                    )
                },
            ),
        )
    }

    /** Transfers an accepted HTTP/1.1 Upgrade exchange to the raw duplex relay path. */
    private fun establishDuplexRelay(
        downstreamContext: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        upstreamChannel: Channel,
        response: com.devuloopers.knet.traffic.model.http.ResponseHead,
        occurredAtEpochMillis: Long,
    ) {
        if (!downstreamContext.executor().inEventLoop()) {
            downstreamContext.executor().execute {
                establishDuplexRelay(
                    downstreamContext,
                    active,
                    upstreamChannel,
                    response,
                    occurredAtEpochMillis,
                )
            }
            return
        }
        if (activeRequest !== active || !downstreamContext.channel().isActive || !upstreamChannel.isActive) {
            upstreamChannel.close()
            return
        }

        active.timings.markLastByteReceived()
        active.duplexInspectors.forEach { inspector ->
            runCatching { inspector.onEstablished(response, occurredAtEpochMillis) }
        }
        active.duplexTransformer?.onEstablished(response, occurredAtEpochMillis)
        activeRequest = null
        releasePendingObjects()

        val lifecycle = DuplexRelayLifecycle(
            inspectors = active.duplexInspectors,
            transformer = active.duplexTransformer,
        ) { outcome, terminatedAt ->
            if (active.exchangeCompleted.compareAndSet(false, true)) {
                active.capture?.terminate(
                    outcome = outcome,
                    timings = active.timings.getTimings(),
                    occurredAtEpochMillis = terminatedAt,
                )
            }
        }
        val downstream = downstreamContext.channel()
        downstream.config().isAutoRead = false
        upstreamChannel.config().isAutoRead = false

        removeHttpHandlersForDuplex(downstream, downstreamSide = true)
        removeHttpHandlersForDuplex(upstreamChannel, downstreamSide = false)
        downstream.pipeline().addLast(
            PipelineHandlerNames.DUPLEX_RELAY,
            KNetDuplexRelayHandler(
                peer = upstreamChannel,
                direction = TrafficDirection.CLIENT_TO_SERVER,
                inspectors = active.duplexInspectors,
                transformer = active.duplexTransformer,
                lifecycle = lifecycle,
            ),
        )
        upstreamChannel.pipeline().addLast(
            PipelineHandlerNames.DUPLEX_RELAY,
            KNetDuplexRelayHandler(
                peer = downstream,
                direction = TrafficDirection.SERVER_TO_CLIENT,
                inspectors = active.duplexInspectors,
                transformer = active.duplexTransformer,
                lifecycle = lifecycle,
            ),
        )
        downstream.read()
        upstreamChannel.read()
    }

    /** Removes only HTTP-object handlers while preserving TLS and timeout ownership. */
    private fun removeHttpHandlersForDuplex(channel: Channel, downstreamSide: Boolean) {
        val pipeline = channel.pipeline()
        val removableNames = buildList {
            add(PipelineHandlerNames.HTTP_AGGREGATOR)
            add(PipelineHandlerNames.SELECTIVE_HTTP_AGGREGATOR)
            add(PipelineHandlerNames.HTTP_CODEC)
            if (downstreamSide) {
                add("knetInterceptorHandler")
                add(PipelineHandlerNames.PROXY_HANDLER)
            } else {
                add(PipelineHandlerNames.OUTBOUND_HANDLER)
            }
        }
        removableNames.forEach { name -> pipeline.get(name)?.let { pipeline.remove(name) } }
        pipeline.toMap().entries
            .filter { (_, handler) ->
                handler is HttpServerCodec ||
                    handler is HttpServerUpgradeHandler ||
                    handler is HttpClientCodec
            }
            .forEach { (name, _) -> pipeline.get(name)?.let { pipeline.remove(name) } }
    }

    /** Recognizes RFC 7230-style HTTP/1.1 Upgrade handshakes without protocol-specific knowledge. */
    private fun isHttpOneUpgradeRequest(request: HttpRequest): Boolean {
        if (request.protocolVersion() != HttpVersion.HTTP_1_1) return false
        val connectionTokens = request.headers()
            .getAll(HttpHeaderNames.CONNECTION)
            .asSequence()
            .flatMap { value -> value.split(',').asSequence() }
            .map(String::trim)
        return connectionTokens.any { token -> token.equals(HttpHeaderValues.UPGRADE.toString(), ignoreCase = true) } &&
            !request.headers().get(HttpHeaderNames.UPGRADE).isNullOrBlank()
    }

    /**
     * Builds an HTTP/2-safe object-bridge request without mutating the HTTP/1 fallback head.
     *
     * Netty deliberately represents one HTTP/2 stream with HTTP/1-shaped objects. The extension
     * scheme header supplies `:scheme`; connection-specific fields are removed before HPACK.
     */
    private fun createHttpTwoUpstreamRequest(source: HttpRequest): HttpRequest {
        val request = DefaultHttpRequest(HttpVersion.HTTP_1_1, source.method(), source.uri())
        request.headers().set(source.headers())
        val nominatedConnectionHeaders = request.headers()
            .getAll(HttpHeaderNames.CONNECTION)
            .asSequence()
            .flatMap { value -> value.split(',').asSequence() }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        nominatedConnectionHeaders.forEach(request.headers()::remove)
        request.headers().remove(HttpHeaderNames.CONNECTION)
        request.headers().remove("Proxy-Connection")
        request.headers().remove("Keep-Alive")
        request.headers().remove(HttpHeaderNames.TRANSFER_ENCODING)
        request.headers().remove(HttpHeaderNames.UPGRADE)
        val teValue = request.headers().get(HttpHeaderNames.TE)
        if (teValue != null && !teValue.equals(HttpHeaderValues.TRAILERS.toString(), ignoreCase = true)) {
            request.headers().remove(HttpHeaderNames.TE)
        }
        HttpTwoBridgeHeaders.prepareForHttpTwo(request.headers(), scheme = "https")
        return request
    }

    /** Unwraps only asynchronous completion wrappers while preserving the typed transport cause. */
    private tailrec fun unwrapCompletionFailure(failure: Throwable?): Throwable? = when (failure) {
        is CompletionException -> unwrapCompletionFailure(failure.cause)
        else -> failure
    }

    /** Handles CONNECT and defers bounded certificate generation until ClientHello reveals SNI. */
    private fun handleConnect(context: ChannelHandlerContext, request: HttpRequest) {
        val parsedAuthority = AuthorityParser.parse(request.uri(), defaultPort = 443)
        if (parsedAuthority !is AuthorityParseResult.Valid) {
            writeBadRequest(context, "Invalid CONNECT authority", request.protocolVersion())
            return
        }
        val host = parsedAuthority.authority.host
        val port = parsedAuthority.authority.port
        context.channel().attr(ProxyChannelAttributes.ROUTE_HOST).set(host)
        context.channel().attr(ProxyChannelAttributes.PORT).set(port)
        context.channel().attr(ProxyChannelAttributes.IS_SSL).set(true)

        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(request.protocolVersion()),
            HttpResponseStatus(200, "Connection Established"),
        )
        response.headers().set("Proxy-Agent", "KNet")
        context.channel().config().isAutoRead = false
        context.writeAndFlush(response).addListener { writeFuture ->
            if (!writeFuture.isSuccess) {
                context.close()
                return@addListener
            }
            try {
                val pipeline = context.pipeline()
                pipeline.get(HttpServerCodec::class.java)?.let(pipeline::remove)
                pipeline.get(PipelineHandlerNames.HTTP_AGGREGATOR)?.let { pipeline.remove(it) }
                pipeline.addFirst(PipelineHandlerNames.SSL, sniTlsContextHandlerFactory.create(context, host))
                val tlsProtocolInstaller = installTlsApplicationProtocol
                if (tlsProtocolInstaller == null) {
                    pipeline.addAfter(
                        PipelineHandlerNames.SSL,
                        PipelineHandlerNames.HTTP_CODEC,
                        HttpServerCodec(),
                    )
                } else {
                    tlsProtocolInstaller(pipeline)
                }
                context.channel().config().isAutoRead = true
            } catch (pipelineFailure: Exception) {
                KNetLogger.error(STREAMING_TAG, pipelineFailure) {
                    "Failed to configure streaming TLS pipeline for $host: ${pipelineFailure.message}"
                }
                context.close()
            }
        }
    }

    /** Resolves absolute/origin-form request routing without accepting malformed authorities. */
    private fun resolveTarget(context: ChannelHandlerContext, request: HttpRequest): ResolvedTarget? {
        val tunnelSsl = context.channel().attr(ProxyChannelAttributes.IS_SSL).get() ?: false
        var isSsl = tunnelSsl
        var routeHost = context.channel().attr(ProxyChannelAttributes.ROUTE_HOST).get()
        var authorityHost: String? = null
        var tlsServerName = context.channel().attr(ProxyChannelAttributes.TLS_SERVER_NAME).get()
        var targetPort = context.channel().attr(ProxyChannelAttributes.PORT).get() ?: if (isSsl) 443 else 80
        val absoluteUri = if (request.uri().startsWith("http://") || request.uri().startsWith("https://")) {
            runCatching { URI.create(request.uri()) }.getOrNull()
        } else {
            null
        }

        if (absoluteUri != null) {
            routeHost = absoluteUri.host
            authorityHost = absoluteUri.host
            tlsServerName = absoluteUri.host
            isSsl = absoluteUri.scheme.equals("https", ignoreCase = true)
            targetPort = when {
                absoluteUri.port != -1 -> absoluteUri.port
                isSsl -> 443
                else -> 80
            }
        } else {
            val hostHeader = request.headers().get(HttpHeaderNames.HOST)
            when (val authority = hostHeader?.let {
                AuthorityParser.parse(it, defaultPort = if (isSsl) 443 else 80)
            }) {
                is AuthorityParseResult.Valid -> {
                    authorityHost = authority.authority.host
                    if (routeHost == null) {
                        routeHost = authority.authority.host
                        targetPort = authority.authority.port
                    }
                }
                null -> if (routeHost == null) {
                    writeBadRequest(context, "Missing or invalid Host authority", request.protocolVersion())
                    return null
                }
                is AuthorityParseResult.Invalid -> {
                    writeBadRequest(context, "Missing or invalid Host authority", request.protocolVersion())
                    return null
                }
            }
        }

        if (routeHost == null) {
            writeBadRequest(context, "Missing target authority", request.protocolVersion())
            return null
        }
        authorityHost = authorityHost ?: tlsServerName ?: routeHost
        tlsServerName = tlsServerName ?: authorityHost
        val localPort = (context.channel().localAddress() as? InetSocketAddress)?.port ?: -1
        if (isSelfTarget(routeHost, targetPort, localPort)) {
            writeBadRequest(context, "Recursive self-proxy connection", request.protocolVersion())
            return null
        }

        val relativeUri = if (absoluteUri != null) {
            val path = absoluteUri.rawPath.ifEmpty { "/" }
            absoluteUri.rawQuery?.let { query -> "$path?$query" } ?: path
        } else {
            request.uri()
        }
        return ResolvedTarget(
            routeHost = routeHost,
            authorityHost = authorityHost,
            tlsServerName = tlsServerName,
            port = targetPort,
            isSsl = isSsl,
            relativeUri = relativeUri,
        )
    }

    /** Queues only objects already decoded after read suspension and rejects an excessive pipeline. */
    private fun enqueuePipelined(context: ChannelHandlerContext, message: HttpObject) {
        val addedHeads = if (message is HttpRequest) 1 else 0
        val addedBytes = (message as? HttpContent)?.content()?.readableBytes()?.toLong() ?: 0L
        if (
            pendingRequestHeads + addedHeads > MAX_PIPELINED_REQUESTS ||
            pendingContentBytes + addedBytes > MAX_ALREADY_DECODED_PIPELINE_BYTES
        ) {
            val requestVersion = activeRequest?.downstreamPolicy?.version ?: (message as? HttpRequest)
                ?.protocolVersion()
                ?: HttpVersion.HTTP_1_1
            ReferenceCountUtil.release(message)
            releasePendingObjects()
            val response = DefaultFullHttpResponse(
                HttpOneSemantics.generatedResponseVersion(requestVersion),
                HttpResponseStatus.TOO_MANY_REQUESTS,
            )
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
            context.writeAndFlush(response).addListener { context.close() }
            return
        }
        pendingRequestHeads += addedHeads
        pendingContentBytes += addedBytes
        pendingObjects.addLast(message)
    }

    /** Completes current ownership, then starts the next already-decoded pipelined request in order. */
    private fun completeExchange(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        keepDownstreamAlive: Boolean,
    ) {
        if (!context.executor().inEventLoop()) {
            context.executor().execute { completeExchange(context, active, keepDownstreamAlive) }
            return
        }
        if (!active.exchangeCompleted.compareAndSet(false, true)) return
        if (activeRequest === active) activeRequest = null
        active.upstreamChannel?.close()
        active.streamTransformer?.cancel(null)
        releaseBodyQueue(active)
        active.transformQueue.clear()

        if (!keepDownstreamAlive || !active.requestEndReceived || !context.channel().isActive) {
            releasePendingObjects()
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
        reason: TrafficTerminationReason,
        causeMessage: String? = null,
    ) {
        if (activeRequest !== active || !active.exchangeCompleted.compareAndSet(false, true)) return
        activeRequest = null
        active.upstreamChannel?.close()
        active.streamTransformer?.cancel(reason)
        releaseBodyQueue(active)
        active.transformQueue.clear()
        if (!active.requestEndReceived) {
            active.capture?.cancelBody(
                direction = TrafficDirection.CLIENT_TO_SERVER,
                observedBytes = active.observedRequestBytes,
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                reason = reason,
            )
        }

        val bodyBytes = causeMessage
            ?.let { "$status: $it" }
            ?.toByteArray(Charsets.UTF_8)
            ?: ByteArray(0)
        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(active.downstreamPolicy.version),
            status,
            Unpooled.wrappedBuffer(bodyBytes),
        )
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bodyBytes.size)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        val now = Clock.System.now().toEpochMilliseconds()
        active.capture?.observeResponse(HttpMapper.mapResponseHead(response), now)
        captureBodyChunk(
            exchange = active.capture,
            direction = TrafficDirection.SERVER_TO_CLIENT,
            content = response.content(),
            contentEncoding = HttpMapper.contentEncoding(response.headers()),
        )
        active.capture?.completeBody(
            direction = TrafficDirection.SERVER_TO_CLIENT,
            observedBytes = bodyBytes.size.toLong(),
            occurredAtEpochMillis = now,
        )
        active.capture?.terminate(
            outcome = ExchangeTerminalOutcome.Failed(reason),
            timings = active.timings.getTimings(),
            occurredAtEpochMillis = now,
        )
        active.streamInspectors.forEach { inspector ->
            runCatching {
                inspector.onExchangeTerminated(ExchangeTerminalOutcome.Failed(reason), now)
            }
        }
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
            active.transformQueue.clear()
            val reason = TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED
            active.streamTransformer?.cancel(reason)
            active.upstreamChannel?.close()
            if (active.exchangeCompleted.compareAndSet(false, true)) {
                active.capture?.cancelBody(
                    direction = TrafficDirection.CLIENT_TO_SERVER,
                    observedBytes = active.observedRequestBytes,
                    occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    reason = reason,
                )
                active.capture?.terminate(
                    outcome = ExchangeTerminalOutcome.Cancelled(reason),
                    timings = active.timings.getTimings(),
                    occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                )
                active.streamInspectors.forEach { inspector ->
                    runCatching {
                        inspector.onExchangeTerminated(
                            ExchangeTerminalOutcome.Cancelled(reason),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                }
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
    private fun writeBadRequest(
        context: ChannelHandlerContext,
        reason: String,
        requestVersion: HttpVersion,
    ) {
        KNetLogger.warn(STREAMING_TAG) { "Rejected streaming proxy request: $reason" }
        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(requestVersion),
            HttpResponseStatus.BAD_REQUEST,
        )
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
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
        val routeHost: String,
        val authorityHost: String,
        val tlsServerName: String,
        val port: Int,
        val isSsl: Boolean,
        val relativeUri: String,
    )

    /** Mutable event-loop-confined ownership for one streaming HTTP/1 exchange. */
    private data class ActiveStreamingRequest(
        val mappedRequest: ProxyRequestContext,
        val target: ResolvedTarget,
        val outboundHead: HttpRequest,
        val downstreamPolicy: HttpOneDownstreamPolicy,
        val capture: ProxyExchangeCapture?,
        val streamInspectors: List<ProxyStreamInspector>,
        val streamTransformer: ProxyStreamTransformer?,
        val duplexInspectors: List<ProxyDuplexInspector>,
        val duplexTransformer: ProxyDuplexTransformer?,
        val contentEncoding: ContentEncoding?,
        val timings: NetworkTimingCollector,
        val requestsDuplexUpgrade: Boolean,
        val bodyQueue: ArrayDeque<HttpContent> = ArrayDeque(),
        val transformQueue: ArrayDeque<TransformInput> = ArrayDeque(),
        val exchangeCompleted: AtomicBoolean = AtomicBoolean(false),
        var upstreamChannel: Channel? = null,
        var requestHeadWritten: Boolean = false,
        var writeInProgress: Boolean = false,
        var transformInProgress: Boolean = false,
        var requestEndReceived: Boolean = false,
        var requestEndWritten: Boolean = false,
        var observedRequestBytes: Long = 0L,
    )

    /** Heap-owned input retained only by an active protocol transformer. */
    private data class TransformInput(
        val payload: ByteArray,
        val isLast: Boolean,
        val trailers: List<com.devuloopers.knet.traffic.model.http.HeaderField>,
    )
}
