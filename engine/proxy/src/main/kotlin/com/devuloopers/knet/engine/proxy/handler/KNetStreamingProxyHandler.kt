package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.ProxyConnectionAdmissionController
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCaptureMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueSecurityProtocol
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueTransportProtocol
import com.devuloopers.knet.engine.proxy.http.*
import com.devuloopers.knet.engine.proxy.inspection.*
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.pipeline.SelectiveHttpObjectAggregator
import com.devuloopers.knet.engine.proxy.ssl.ProxyTrustManager
import com.devuloopers.knet.engine.proxy.timing.NetworkTimingCollector
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.devuloopers.knet.engine.proxy.tls.SniTlsContextHandlerFactory
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionDecision
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionMode
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionPolicy
import com.devuloopers.knet.engine.proxy.tls.TlsInterceptionRequest
import com.devuloopers.knet.engine.proxy.tls.TlsInspectionOutcomeHandler
import com.devuloopers.knet.engine.proxy.tls.TlsSourceApplicationId
import com.devuloopers.knet.engine.proxy.tls.TlsClientHelloMetadata
import com.devuloopers.knet.engine.proxy.tls.TlsClientHelloPolicyHandler
import com.devuloopers.knet.engine.proxy.upstream.*
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import kotlinx.coroutines.CoroutineScope
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock

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
    serverTlsContextProvider: ServerTlsContextProvider,
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
    private val upstreamAddressResolver: UpstreamAddressResolver = CoroutineUpstreamAddressResolver(
        scope = proxyScope,
        maximumCandidates = runtimePolicy.maximumUpstreamAddressCandidates,
    ),
    private val happyEyeballsDialer: HappyEyeballsDialer = HappyEyeballsDialer(runtimePolicy),
    private val installTlsApplicationProtocol: ((io.netty.channel.ChannelPipeline) -> Unit)? = null,
    private val streamInspectorFactories: List<ProxyStreamInspectorFactory> = emptyList(),
    private val streamTransformerFactories: List<ProxyStreamTransformerFactory> = emptyList(),
    private val duplexInspectorFactories: List<ProxyDuplexInspectorFactory> = emptyList(),
    private val duplexTransformerFactories: List<ProxyDuplexTransformerFactory> = emptyList(),
    private val tlsInterceptionPolicy: TlsInterceptionPolicy = TlsInterceptionPolicy.InspectAll,
) : ChannelInboundHandlerAdapter() {

    companion object {
        private const val MAX_PIPELINED_REQUESTS: Int = 16
        private const val MAX_ALREADY_DECODED_PIPELINE_BYTES: Long = 1L * 1024L * 1024L
        private const val TLS_CLIENT_HELLO_POLICY_HANDLER: String = "knetTlsClientHelloPolicy"
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
    private var connectTransitionStarted: Boolean = false

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
            appliedNetworkCondition = context.channel()
                .attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION)
                .get(),
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

    /** Resolves every DNS candidate away from the event loop, then selects HTTP/2 or HTTP/1. */
    private fun connectUpstream(context: ChannelHandlerContext, active: ActiveStreamingRequest) {
        upstreamAddressResolver.resolve(
            host = active.target.routeHost,
            port = active.target.port,
            fallbackDnsHost = active.target.fallbackRouteHost,
        )
            .whenComplete { route, resolutionFailure ->
                context.executor().execute {
                    if (activeRequest !== active || !context.channel().isActive) return@execute
                    active.timings.markDnsEnd()
                    if (resolutionFailure != null || route == null) {
                        failExchange(
                            context = context,
                            active = active,
                            status = HttpResponseStatus.BAD_GATEWAY,
                            reason = TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                            causeMessage = unwrapCompletionFailure(resolutionFailure)?.message
                                ?: "DNS returned no usable upstream address.",
                        )
                        return@execute
                    }
                    if (active.target.isSsl && httpTwoUpstreamPool != null && !active.requestsDuplexUpgrade) {
                        connectHttpTwo(context, active, route)
                    } else {
                        connectHttpOne(context, active, route)
                    }
                }
            }
    }

    /** Opens an isolated stream on a pooled TLS HTTP/2 parent. */
    private fun connectHttpTwo(
        context: ChannelHandlerContext,
        active: ActiveStreamingRequest,
        resolvedRoute: ResolvedUpstreamRoute,
    ) {
        val pool = checkNotNull(httpTwoUpstreamPool)
        active.timings.markTcpStart()
        active.timings.markTlsStart()
        val httpTwoRequest = createHttpTwoUpstreamRequest(active.outboundHead)
        pool.openStream(
            eventLoop = context.channel().eventLoop(),
            route = HttpTwoUpstreamRoute(
                host = active.target.tlsServerName,
                resolvedRoute = resolvedRoute,
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
                    connectHttpOne(context, active, resolvedRoute)
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
        resolvedRoute: ResolvedUpstreamRoute,
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

        happyEyeballsDialer.connect(context.channel().eventLoop(), resolvedRoute)
            .whenComplete { connected, connectionFailure ->
                context.executor().execute {
                    active.timings.markTcpEnd()
                    if (activeRequest !== active || !context.channel().isActive) {
                        connected?.channel?.close()
                        upstreamLease.close()
                        return@execute
                    }
                    if (connectionFailure == null && connected != null) {
                        val upstreamChannel = connected.channel as SocketChannel
                        configureHttpOneUpstreamPipeline(context, active, upstreamChannel)
                        active.upstreamChannel = upstreamChannel
                        upstreamChannel.closeFuture().addListener { upstreamLease.close() }
                        pumpRequestBody(context, active)
                    } else {
                        upstreamLease.close()
                        failExchange(
                            context = context,
                            active = active,
                            status = HttpResponseStatus.BAD_GATEWAY,
                            reason = TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                            causeMessage = unwrapCompletionFailure(connectionFailure)?.message,
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
                .apply { handshakeTimeoutMillis = runtimePolicy.tlsHandshakeTimeoutMillis }
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
        if (connectTransitionStarted) {
            writeConnectFailure(context, request.protocolVersion(), HttpResponseStatus.CONFLICT)
            return
        }
        val parsedAuthority = AuthorityParser.parse(request.uri(), defaultPort = 443)
        if (parsedAuthority !is AuthorityParseResult.Valid) {
            writeBadRequest(context, "Invalid CONNECT authority", request.protocolVersion())
            return
        }
        val host = parsedAuthority.authority.host
        val port = parsedAuthority.authority.port
        val requestVersion = request.protocolVersion()
        context.channel().attr(ProxyChannelAttributes.ROUTE_HOST).set(host)
        context.channel().attr(ProxyChannelAttributes.PORT).set(port)
        context.channel().attr(ProxyChannelAttributes.IS_SSL).set(true)

        val decision = runCatching {
            tlsInterceptionPolicy.decide(
                TlsInterceptionRequest(
                    connectHost = host,
                    connectPort = port,
                    sourceApplication = sourceApplication(context),
                ),
            )
        }.getOrElse { failure ->
            KNetLogger.warn(STREAMING_TAG) {
                "Protected traffic policy failed for $host:$port: ${failure::class.simpleName}"
            }
            TlsInterceptionDecision(TlsInterceptionMode.BLOCK)
        }
        connectTransitionStarted = true
        when (decision.mode) {
            TlsInterceptionMode.INSPECT -> installClassifiedConnect(context, requestVersion, host, port)
            TlsInterceptionMode.TUNNEL -> openRawTunnel(context, requestVersion, host, port, decision)
            TlsInterceptionMode.BLOCK -> blockProtectedTraffic(context, requestVersion, host, port, decision)
        }
    }

    /** Sends CONNECT success and classifies one bounded ClientHello before certificate generation. */
    private fun installClassifiedConnect(
        context: ChannelHandlerContext,
        requestVersion: HttpVersion,
        host: String,
        port: Int,
    ) {
        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(requestVersion),
            HttpResponseStatus(200, "Connection Established"),
        )
        response.headers().set("Proxy-Agent", "KNet")
        context.channel().config().isAutoRead = false
        context.writeAndFlush(response).addListener { writeFuture ->
            context.executor().execute {
                if (!writeFuture.isSuccess || !context.channel().isActive) {
                    context.close()
                    return@execute
                }
                try {
                    removeDownstreamHttpHandlers(context.pipeline())
                    context.pipeline().addBefore(
                        context.name(),
                        TLS_CLIENT_HELLO_POLICY_HANDLER,
                        TlsClientHelloPolicyHandler(
                            maximumClientHelloBytes = runtimePolicy.maximumTlsClientHelloBytes,
                            handshakeTimeoutMillis = runtimePolicy.tlsHandshakeTimeoutMillis,
                            lookupRoute = { classifierContext, metadata ->
                                prepareClassifiedConnectRoute(classifierContext, host, port, metadata)
                            },
                            installRoute = { classifierContext, route, metadata ->
                                installClassifiedConnectRoute(classifierContext, host, route, metadata)
                            },
                        ),
                    )
                    context.channel().config().isAutoRead = true
                } catch (failure: Exception) {
                    KNetLogger.error(STREAMING_TAG, failure) {
                        "Failed to install ClientHello classifier for $host:$port"
                    }
                    context.close()
                }
            }
        }
    }

    /** Evaluates SNI-aware policy and completes only after any raw upstream is ready. */
    private fun prepareClassifiedConnectRoute(
        context: ChannelHandlerContext,
        host: String,
        port: Int,
        metadata: TlsClientHelloMetadata,
    ): Future<ClassifiedConnectRoute> {
        val decision = runCatching {
            tlsInterceptionPolicy.decide(
                TlsInterceptionRequest(
                    connectHost = host,
                    connectPort = port,
                    serverName = metadata.serverName,
                    sourceApplication = sourceApplication(context),
                ),
            )
        }.getOrElse { failure ->
            KNetLogger.warn(STREAMING_TAG) {
                "SNI-aware protected traffic policy failed for $host:$port: ${failure::class.simpleName}"
            }
            TlsInterceptionDecision(TlsInterceptionMode.BLOCK)
        }
        return when (decision.mode) {
            TlsInterceptionMode.INSPECT -> context.executor().newSucceededFuture(
                ClassifiedConnectRoute.Inspect(decision),
            )
            TlsInterceptionMode.BLOCK -> context.executor().newSucceededFuture(
                ClassifiedConnectRoute.Block(decision),
            )
            TlsInterceptionMode.TUNNEL -> prepareClassifiedRawTunnel(
                context = context,
                host = host,
                port = port,
                decision = decision,
                metadata = metadata,
            )
        }
    }

    /** Resolves and dials a raw upstream while the bounded classifier retains the original hello. */
    private fun prepareClassifiedRawTunnel(
        context: ChannelHandlerContext,
        host: String,
        port: Int,
        decision: TlsInterceptionDecision,
        metadata: TlsClientHelloMetadata,
    ): Future<ClassifiedConnectRoute> {
        val promise: Promise<ClassifiedConnectRoute> = context.executor().newPromise()
        val capture = startOpaqueTlsCapture(context, host, port, metadata.serverName, decision, metadata)
        upstreamAddressResolver.resolve(host, port, fallbackDnsHost = null)
            .whenComplete { resolvedRoute, resolutionFailure ->
                context.executor().execute {
                    if (!context.channel().isActive) {
                        terminateOpaqueCapture(
                            capture,
                            ExchangeTerminalOutcome.Cancelled(
                                TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED,
                            ),
                        )
                        promise.tryFailure(IllegalStateException("Downstream closed during protected route lookup."))
                        return@execute
                    }
                    if (resolutionFailure != null || resolvedRoute == null) {
                        terminateOpaqueCapture(
                            capture,
                            ExchangeTerminalOutcome.Failed(
                                TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                            ),
                        )
                        promise.tryFailure(
                            resolutionFailure ?: IllegalStateException("Protected route resolution failed."),
                        )
                        return@execute
                    }
                    val lease = admissionController.tryAcquireUpstream()
                    if (lease == null) {
                        terminateOpaqueCapture(
                            capture,
                            ExchangeTerminalOutcome.Failed(
                                TrafficTerminationReason.Transport.UPSTREAM_CONNECTION_LIMIT,
                            ),
                        )
                        promise.tryFailure(IllegalStateException("Protected upstream connection limit reached."))
                        return@execute
                    }
                    happyEyeballsDialer.connect(context.channel().eventLoop(), resolvedRoute)
                        .whenComplete { connected, connectFailure ->
                            context.executor().execute {
                                if (connectFailure != null || connected == null || !context.channel().isActive) {
                                    connected?.channel?.close()
                                    lease.close()
                                    terminateOpaqueCapture(
                                        capture,
                                        ExchangeTerminalOutcome.Failed(
                                            TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                                        ),
                                    )
                                    promise.tryFailure(
                                        connectFailure ?: IllegalStateException(
                                            "Protected upstream connection failed.",
                                        ),
                                    )
                                } else {
                                    connected.channel.closeFuture().addListener { lease.close() }
                                    promise.trySuccess(ClassifiedConnectRoute.Tunnel(connected.channel, capture))
                                }
                            }
                        }
                }
            }
        return promise
    }

    /** Atomically replaces the classifier with either TLS interception, raw relay, or policy close. */
    private fun installClassifiedConnectRoute(
        context: ChannelHandlerContext,
        connectHost: String,
        route: ClassifiedConnectRoute,
        metadata: TlsClientHelloMetadata,
    ) {
        when (route) {
            is ClassifiedConnectRoute.Inspect -> {
                context.pipeline().replace(
                    context.name(),
                    PipelineHandlerNames.SSL,
                    sniTlsContextHandlerFactory.create(context, connectHost),
                )
                context.pipeline().addAfter(
                    PipelineHandlerNames.SSL,
                    PipelineHandlerNames.TLS_INSPECTION_OUTCOME,
                    TlsInspectionOutcomeHandler {
                        startOpaqueTlsCapture(
                            context = context,
                            host = connectHost,
                            port = context.channel().attr(ProxyChannelAttributes.PORT).get() ?: 443,
                            serverName = metadata.serverName,
                            decision = route.decision,
                            metadata = metadata,
                        )?.terminate(
                            ExchangeTerminalOutcome.Failed(
                                TrafficTerminationReason.Transport.DOWNSTREAM_TLS_HANDSHAKE_FAILED,
                            ),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                    },
                )
                installDownstreamTlsProtocol(context.pipeline())
            }
            is ClassifiedConnectRoute.Tunnel -> installRawTunnelRelay(
                context = context,
                upstream = route.upstream,
                capture = route.capture,
            )
            is ClassifiedConnectRoute.Block -> {
                startOpaqueTlsCapture(
                    context,
                    connectHost,
                    context.channel().attr(ProxyChannelAttributes.PORT).get() ?: 443,
                    metadata.serverName,
                    route.decision,
                    metadata,
                )?.terminate(
                    ExchangeTerminalOutcome.Dropped(
                        TrafficTerminationReason.Interception.PROTECTED_TRAFFIC_BLOCKED,
                    ),
                    Clock.System.now().toEpochMilliseconds(),
                )
                context.close()
            }
        }
    }

    /** Installs the HTTP object protocol chosen after downstream TLS terminates. */
    private fun installDownstreamTlsProtocol(pipeline: io.netty.channel.ChannelPipeline) {
        val tlsProtocolInstaller = installTlsApplicationProtocol
        if (tlsProtocolInstaller == null) {
            pipeline.addAfter(PipelineHandlerNames.SSL, PipelineHandlerNames.HTTP_CODEC, HttpServerCodec())
        } else {
            tlsProtocolInstaller(pipeline)
        }
    }

    /** Installs the existing certificate-generating TLS interception pipeline. */
    private fun installInterceptedConnect(
        context: ChannelHandlerContext,
        requestVersion: HttpVersion,
        host: String,
    ) {
        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(requestVersion),
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
                installDownstreamTlsProtocol(pipeline)
                context.channel().config().isAutoRead = true
            } catch (pipelineFailure: Exception) {
                KNetLogger.error(STREAMING_TAG, pipelineFailure) {
                    "Failed to configure streaming TLS pipeline for $host: ${pipelineFailure.message}"
                }
                context.close()
            }
        }
    }

    /** Opens an end-to-end encrypted raw tunnel without generating a downstream certificate. */
    private fun openRawTunnel(
        context: ChannelHandlerContext,
        requestVersion: HttpVersion,
        host: String,
        port: Int,
        decision: TlsInterceptionDecision,
    ) {
        context.channel().config().isAutoRead = false
        val capture = startOpaqueTlsCapture(context, host, port, serverName = null, decision)
        upstreamAddressResolver.resolve(host, port, fallbackDnsHost = null)
            .whenComplete { resolvedRoute, resolutionFailure ->
                context.executor().execute {
                    if (!context.channel().isActive) {
                        capture?.terminate(
                            ExchangeTerminalOutcome.Cancelled(
                                TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED,
                            ),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                        return@execute
                    }
                    if (resolutionFailure != null || resolvedRoute == null) {
                        capture?.terminate(
                            ExchangeTerminalOutcome.Failed(
                                TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                            ),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                        writeConnectFailure(
                            context,
                            requestVersion,
                            HttpResponseStatus.BAD_GATEWAY,
                        )
                        return@execute
                    }
                    connectRawTunnel(context, requestVersion, resolvedRoute, capture)
                }
            }
    }

    /** Acquires bounded upstream admission before entering the shared dual-stack dialer. */
    private fun connectRawTunnel(
        context: ChannelHandlerContext,
        requestVersion: HttpVersion,
        resolvedRoute: ResolvedUpstreamRoute,
        capture: ProxyOpaqueFlowCapture?,
    ) {
        val upstreamLease = admissionController.tryAcquireUpstream()
        if (upstreamLease == null) {
            capture?.terminate(
                ExchangeTerminalOutcome.Failed(
                    TrafficTerminationReason.Transport.UPSTREAM_CONNECTION_LIMIT,
                ),
                Clock.System.now().toEpochMilliseconds(),
            )
            writeConnectFailure(context, requestVersion, HttpResponseStatus.SERVICE_UNAVAILABLE)
            return
        }
        happyEyeballsDialer.connect(context.channel().eventLoop(), resolvedRoute)
            .whenComplete { connected, connectionFailure ->
                context.executor().execute {
                    if (!context.channel().isActive) {
                        connected?.channel?.close()
                        upstreamLease.close()
                        capture?.terminate(
                            ExchangeTerminalOutcome.Cancelled(
                                TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED,
                            ),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                        return@execute
                    }
                    if (connectionFailure != null || connected == null) {
                        upstreamLease.close()
                        capture?.terminate(
                            ExchangeTerminalOutcome.Failed(
                                TrafficTerminationReason.Transport.UPSTREAM_CONNECT_FAILED,
                            ),
                            Clock.System.now().toEpochMilliseconds(),
                        )
                        writeConnectFailure(
                            context,
                            requestVersion,
                            HttpResponseStatus.BAD_GATEWAY,
                        )
                        return@execute
                    }
                    val upstream = connected.channel
                    upstream.closeFuture().addListener { upstreamLease.close() }
                    installRawTunnel(context, requestVersion, upstream, capture)
                }
            }
    }

    /** Sends CONNECT success through the HTTP encoder, then atomically switches both channels to raw relay. */
    private fun installRawTunnel(
        context: ChannelHandlerContext,
        requestVersion: HttpVersion,
        upstream: Channel,
        capture: ProxyOpaqueFlowCapture?,
    ) {
        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(requestVersion),
            HttpResponseStatus(200, "Connection Established"),
        )
        response.headers().set("Proxy-Agent", "KNet")
        context.writeAndFlush(response).addListener { responseWrite ->
            context.executor().execute {
                if (!responseWrite.isSuccess || !context.channel().isActive) {
                    upstream.close()
                    capture?.terminate(
                        ExchangeTerminalOutcome.Failed(
                            TrafficTerminationReason.Transport.DOWNSTREAM_RESPONSE_REJECTED,
                        ),
                        Clock.System.now().toEpochMilliseconds(),
                    )
                    context.close()
                    return@execute
                }

                installRawTunnelRelay(context, upstream, capture)
            }
        }
    }

    /** Installs both directions of an already-connected protected raw tunnel. */
    private fun installRawTunnelRelay(
        context: ChannelHandlerContext,
        upstream: Channel,
        capture: ProxyOpaqueFlowCapture?,
    ) {
        val lifecycle = RawTunnelLifecycle(capture)
        val downstreamPipeline = context.pipeline()
        removeDownstreamHttpHandlers(downstreamPipeline)
        downstreamPipeline.get(PipelineHandlerNames.PROXY_HANDLER)
            ?.takeIf { handler -> downstreamPipeline.context(handler)?.name() != context.name() }
            ?.let(downstreamPipeline::remove)
        downstreamPipeline.replace(
            context.name(),
            PipelineHandlerNames.DUPLEX_RELAY,
            KNetRawTunnelRelayHandler(
                peer = upstream,
                direction = TrafficDirection.CLIENT_TO_SERVER,
                capture = capture,
                lifecycle = lifecycle,
            ),
        )
        upstream.pipeline().addLast(
            PipelineHandlerNames.READ_TIMEOUT,
            ReadTimeoutHandler(runtimePolicy.readIdleTimeoutMillis, TimeUnit.MILLISECONDS),
        )
        upstream.pipeline().addLast(
            PipelineHandlerNames.WRITE_TIMEOUT,
            WriteTimeoutHandler(runtimePolicy.writeIdleTimeoutMillis, TimeUnit.MILLISECONDS),
        )
        upstream.pipeline().addLast(
            PipelineHandlerNames.DUPLEX_RELAY,
            KNetRawTunnelRelayHandler(
                peer = context.channel(),
                direction = TrafficDirection.SERVER_TO_CLIENT,
                capture = capture,
                lifecycle = lifecycle,
            ),
        )
        upstream.read()
        context.channel().read()
    }

    /** Removes HTTP decoders before either TLS or raw ClientHello bytes are forwarded. */
    private fun removeDownstreamHttpHandlers(pipeline: io.netty.channel.ChannelPipeline) {
        pipeline.get(PipelineHandlerNames.HTTP_AGGREGATOR)?.let(pipeline::remove)
        pipeline.get(PipelineHandlerNames.SELECTIVE_HTTP_AGGREGATOR)?.let(pipeline::remove)
        pipeline.get(PipelineHandlerNames.HTTP_CODEC)?.let(pipeline::remove)
        pipeline.toMap().entries
            .filter { (_, handler) -> handler is HttpServerCodec || handler is HttpServerUpgradeHandler }
            .forEach { (name, _) -> pipeline.get(name)?.let(pipeline::remove) }
    }

    /** Starts payload-blind TLS-flow capture with exact policy evidence. */
    private fun startOpaqueTlsCapture(
        context: ChannelHandlerContext,
        host: String,
        port: Int,
        serverName: String?,
        decision: TlsInterceptionDecision,
        metadata: TlsClientHelloMetadata? = null,
    ): ProxyOpaqueFlowCapture? = connectionCapture?.startOpaqueFlow(
        ProxyOpaqueFlowCaptureMetadata(
            destination = com.devuloopers.knet.traffic.model.TrafficEndpoint(host, port),
            serverName = serverName,
            transport = ProxyOpaqueTransportProtocol.TCP,
            security = ProxyOpaqueSecurityProtocol.TLS,
            policyRuleId = decision.ruleId?.value,
            policyAction = com.devuloopers.knet.engine.proxy.capture.ProxyOpaquePolicyAction.valueOf(
                decision.policyAction.name,
            ),
            policyGroupId = decision.policyGroupId,
            sourceApplicationId = context.channel().attr(ProxyChannelAttributes.SOURCE_APPLICATION_ID).get(),
            appliedNetworkCondition = context.channel()
                .attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION)
                .get(),
            occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            offeredApplicationProtocols = metadata?.offeredApplicationProtocols.orEmpty(),
            offeredTlsVersions = metadata?.offeredTlsVersions.orEmpty(),
        ),
    )

    /** Returns a validated typed source identity supplied by the trusted ingress boundary. */
    private fun sourceApplication(context: ChannelHandlerContext): com.devuloopers.knet.engine.proxy.tls.TlsSourceApplicationId? =
        context.channel().attr(ProxyChannelAttributes.SOURCE_APPLICATION_ID).get()
            ?.let(::TlsSourceApplicationId)

    /** Publishes one terminal capture result at the current clock boundary. */
    private fun terminateOpaqueCapture(capture: ProxyOpaqueFlowCapture?, outcome: ExchangeTerminalOutcome) {
        capture?.terminate(outcome, Clock.System.now().toEpochMilliseconds())
    }

    /** Records and rejects a policy-blocked protected connection before upstream dialing. */
    private fun blockProtectedTraffic(
        context: ChannelHandlerContext,
        requestVersion: HttpVersion,
        host: String,
        port: Int,
        decision: TlsInterceptionDecision,
    ) {
        startOpaqueTlsCapture(context, host, port, serverName = null, decision)?.terminate(
            ExchangeTerminalOutcome.Dropped(
                TrafficTerminationReason.Interception.PROTECTED_TRAFFIC_BLOCKED,
            ),
            Clock.System.now().toEpochMilliseconds(),
        )
        writeConnectFailure(context, requestVersion, HttpResponseStatus.FORBIDDEN)
    }

    /** Writes a terminal CONNECT response while the HTTP encoder still owns the pipeline. */
    private fun writeConnectFailure(
        context: ChannelHandlerContext,
        requestVersion: HttpVersion,
        status: HttpResponseStatus,
    ) {
        val response = DefaultFullHttpResponse(
            HttpOneSemantics.generatedResponseVersion(requestVersion),
            status,
        )
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        context.writeAndFlush(response).addListener { context.close() }
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
        val routeSelection = UpstreamRouteHostSelector.select(
            connectHost = routeHost,
            tlsServerName = tlsServerName,
            isTls = isSsl,
        )
        if (routeSelection.fallbackDnsHost != null) {
            KNetLogger.debug(STREAMING_TAG) {
                "upstream_event=route_host_fallback_available connect_host=$routeHost " +
                        "fallback_host=${routeSelection.fallbackDnsHost}"
            }
        }

        val relativeUri = if (absoluteUri != null) {
            val path = absoluteUri.rawPath.ifEmpty { "/" }
            absoluteUri.rawQuery?.let { query -> "$path?$query" } ?: path
        } else {
            request.uri()
        }
        return ResolvedTarget(
            routeHost = routeSelection.primaryHost,
            fallbackRouteHost = routeSelection.fallbackDnsHost,
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
        val fallbackRouteHost: String?,
        val authorityHost: String,
        val tlsServerName: String,
        val port: Int,
        val isSsl: Boolean,
        val relativeUri: String,
    )

    /** Prepared result of one bounded ClientHello policy lookup. */
    private sealed interface ClassifiedConnectRoute {
        /** Continue through KNet's certificate-backed TLS inspection pipeline. */
        data class Inspect(val decision: TlsInterceptionDecision) : ClassifiedConnectRoute

        /** Relay the original ClientHello and all following bytes through [upstream]. */
        data class Tunnel(
            val upstream: Channel,
            val capture: ProxyOpaqueFlowCapture?,
        ) : ClassifiedConnectRoute

        /** Close the already-established CONNECT stream without dialing the destination. */
        data class Block(val decision: TlsInterceptionDecision) : ClassifiedConnectRoute
    }

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
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TransformInput

            if (isLast != other.isLast) return false
            if (!payload.contentEquals(other.payload)) return false
            if (trailers != other.trailers) return false

            return true
        }

        override fun hashCode(): Int {
            var result = isLast.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + trailers.hashCode()
            return result
        }
    }
}
