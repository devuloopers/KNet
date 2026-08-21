package com.devuloopers.knet.engine.proxy

import com.devuloopers.knet.engine.proxy.handler.KNetStreamingProxyHandler
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.upstream.HttpTwoUpstreamConnectionPool
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.group.ChannelGroup
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerUpgradeHandler
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.ResourceLeakDetector
import io.netty.util.AsciiString
import io.netty.util.concurrent.GlobalEventExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import io.netty.util.concurrent.ScheduledFuture
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider


/**
 * Netty-based asynchronous proxy server that listens on a port, intercepts HTTP/HTTPS requests,
 * and routes them through the interception pipeline.
 *
 * @property bindHost Explicit listener host. The safe default is IPv4 loopback.
 * @property port The port KNet will bind to (default: 8080).
 * @property serverTlsContextProvider Supplies per-host server TLS contexts without exposing key material.
 * @property keyManagerProvider Optional upstream client-identity selector for mTLS.
 * @property verifyUpstreamTls Whether upstream server certificates must be verified.
 * @property runtimePolicy Enforced connection and timeout limits for this runtime.
 * @property pipelineInitializers Instance-owned pipeline extensions installed by composition.
 * @property requiresFullResponseAggregation Per-request inspection decision supplied by composition.
 * False keeps that response streaming; true uses overflow-safe bounded aggregation.
 * @property runtimeMetrics Runtime-owned constant-time operational metrics sink.
 */
class KNetProxyServer(
    val bindHost: String = DEFAULT_BIND_HOST,
    val port: Int = 8080,
    private val serverTlsContextProvider: ServerTlsContextProvider,
    private val keyManagerProvider: com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider? = null,
    private val verifyUpstreamTls: Boolean = true,
    private val runtimePolicy: KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(),
    private val pipelineInitializers: List<(io.netty.channel.ChannelPipeline) -> Unit> = emptyList(),
    private val captureSink: ProxyCaptureSink? = null,
    private val ingressContext: IngressContext = IngressContext(IngressKind.Local),
    private val ingressAttribution: IngressAttributionLookup? = null,
    private val requiresFullResponseAggregation: (HttpRequestSnapshot) -> Boolean = { false },
    private val runtimeMetrics: ProxyRuntimeMetrics = ProxyRuntimeMetrics(),
) {

    companion object {
        /** Safe listener address used unless an explicit exposure policy supplies another host. */
        const val DEFAULT_BIND_HOST: String = "127.0.0.1"

        private const val CERTIFICATE_WORKERS: Int = 2
        private const val CERTIFICATE_QUEUE_CAPACITY: Int = 128
        private const val EVENT_LOOP_LAG_SAMPLE_MILLIS: Long = 100L

    }

    private val isStarted = AtomicBoolean(false)
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private var serverScope: kotlinx.coroutines.CoroutineScope? = null
    private var certificateExecutor: ThreadPoolExecutor? = null
    private var eventLoopLagTask: ScheduledFuture<*>? = null
    private var httpTwoUpstreamPool: HttpTwoUpstreamConnectionPool? = null
    private val activeChannels: ChannelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    private val admissionController = ProxyConnectionAdmissionController(runtimePolicy)

    init {
        require(bindHost.isNotBlank()) { "Proxy bind host must not be blank." }
        require(port in 1..65_535) { "Proxy bind port must be between 1 and 65535." }
    }

    /**
     * Starts the Netty proxy server.
     * Initializes NIO event loops, bootstrap rules, pipeline codecs, and binds to the socket.
     *
     * @throws IllegalStateException if the server is already running.
     */
    @Synchronized
    fun start() {
        if (!isStarted.compareAndSet(false, true)) {
            throw IllegalStateException("KNetProxyServer is already running.")
        }

        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)

        try {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            serverScope = scope
            val cryptoExecutor = createCertificateExecutor()
            certificateExecutor = cryptoExecutor

            bossGroup = NioEventLoopGroup(1)
            workerGroup = NioEventLoopGroup()
            httpTwoUpstreamPool = HttpTwoUpstreamConnectionPool(
                runtimePolicy = runtimePolicy,
                admissionController = admissionController,
                keyManagerProvider = keyManagerProvider,
                verifyUpstreamTls = verifyUpstreamTls,
            )
            startEventLoopLagMonitor(workerGroup!!)

            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childOption(ChannelOption.ALLOW_HALF_CLOSURE, true)
                .handler(LoggingHandler(LogLevel.DEBUG))
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        val remoteAddress = channel.remoteAddress()
                        val clientKey = remoteAddress.address?.hostAddress ?: remoteAddress.hostString
                        val downstreamLease = admissionController.tryAcquireDownstream(clientKey)
                        if (downstreamLease == null) {
                            channel.close()
                            return
                        }
                        channel.closeFuture().addListener { downstreamLease.close() }
                        val connectionCapture = captureSink?.let { sink ->
                            val local = channel.localAddress()
                            val downstream = TrafficEndpoint(remoteAddress.hostString, remoteAddress.port)
                            val attributedIngress = ingressAttribution?.claim(downstream) ?: ingressContext
                            runCatching {
                                sink.openConnection(
                                    ProxyCaptureConnectionMetadata(
                                        ingress = attributedIngress,
                                        downstream = downstream,
                                        localListener = TrafficEndpoint(local.hostString, local.port),
                                    )
                                )
                            }.getOrNull()
                        }
                        channel.attr(ProxyChannelAttributes.CONNECTION_CAPTURE).set(connectionCapture)
                        channel.closeFuture().addListener { connectionCapture?.close() }
                        activeChannels.add(channel)
                        val pipeline = channel.pipeline()

                        pipeline.addLast(
                            PipelineHandlerNames.READ_TIMEOUT,
                            ReadTimeoutHandler(runtimePolicy.readIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                        )
                        pipeline.addLast(
                            PipelineHandlerNames.WRITE_TIMEOUT,
                            WriteTimeoutHandler(runtimePolicy.writeIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                        )
                        installCleartextApplicationPipeline(
                            channel = channel,
                            scope = scope,
                            cryptoExecutor = cryptoExecutor,
                            connectionCapture = connectionCapture,
                        )
                    }
                })

            serverChannel = bootstrap.bind(InetSocketAddress(bindHost, port)).sync().channel()
        } catch (failure: Throwable) {
            // Startup is atomic: a failed bind or pipeline bootstrap must not retain threads or started state.
            stop()
            throw failure
        }
    }

    /**
     * Closes all active client channels while keeping the listener available for immediate reconnects.
     *
     * Connectivity adapters may use this boundary after a real network transition. Breakpoint rule
     * changes do not require it because selective decisions are evaluated for each request.
     */
    fun closeActiveConnections() {
        activeChannels.close().syncUninterruptibly()
    }

    /** Returns non-blocking operational metrics for this runtime instance. */
    fun metricsSnapshot(): ProxyRuntimeMetricsSnapshot = runtimeMetrics.snapshot()

    /**
     * Stops the Netty proxy server.
     * Releases event loops, closes open connection channels, and releases port bindings.
     */
    @Synchronized
    fun stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return
        }

        serverScope?.cancel()
        serverScope = null
        eventLoopLagTask?.cancel(false)
        eventLoopLagTask = null
        closeActiveConnections()
        httpTwoUpstreamPool?.close()
        httpTwoUpstreamPool = null
        serverChannel?.close()?.syncUninterruptibly()
        serverChannel = null

        certificateExecutor?.shutdownNow()
        certificateExecutor?.awaitTermination(
            runtimePolicy.gracefulShutdownTimeoutMillis,
            TimeUnit.MILLISECONDS,
        )
        certificateExecutor = null

        bossGroup?.shutdownGracefully(
            100,
            runtimePolicy.gracefulShutdownTimeoutMillis,
            TimeUnit.MILLISECONDS,
        )?.syncUninterruptibly()
        workerGroup?.shutdownGracefully(
            100,
            runtimePolicy.gracefulShutdownTimeoutMillis,
            TimeUnit.MILLISECONDS,
        )?.syncUninterruptibly()
        bossGroup = null
        workerGroup = null
    }

    /** Samples scheduling delay without performing work outside atomic metric updates. */
    private fun startEventLoopLagMonitor(group: EventLoopGroup) {
        val eventLoop = group.next()
        var expectedAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(EVENT_LOOP_LAG_SAMPLE_MILLIS)
        eventLoopLagTask = eventLoop.scheduleAtFixedRate(
            {
                val now = System.nanoTime()
                runtimeMetrics.recordEventLoopLagNanos(now - expectedAtNanos)
                expectedAtNanos = now + TimeUnit.MILLISECONDS.toNanos(EVENT_LOOP_LAG_SAMPLE_MILLIS)
            },
            EVENT_LOOP_LAG_SAMPLE_MILLIS,
            EVENT_LOOP_LAG_SAMPLE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }


    /**
     * Returns true if the proxy server is currently running and listening for traffic.
     */
    fun isRunning(): Boolean = isStarted.get()

    /**
     * Returns the bound local address only after startup has completed successfully.
     *
     * @return Active listener address, or `null` while stopped.
     */
    fun boundAddress(): InetSocketAddress? = serverChannel?.localAddress() as? InetSocketAddress

    /** Installs HTTP/1 fallback plus h2c prior-knowledge and Upgrade selection on one socket. */
    private fun installCleartextApplicationPipeline(
        channel: SocketChannel,
        scope: CoroutineScope,
        cryptoExecutor: ThreadPoolExecutor,
        connectionCapture: com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture?,
    ) {
        val pipeline = channel.pipeline()
        val sourceCodec = HttpServerCodec()
        val upgradeHttp2 = createHttp2Components(channel, scope, cryptoExecutor, connectionCapture)
        val upgradeHandler = HttpServerUpgradeHandler(
            sourceCodec,
            HttpServerUpgradeHandler.UpgradeCodecFactory { protocol ->
                if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                    Http2ServerUpgradeCodec(upgradeHttp2.frameCodec, upgradeHttp2.multiplexHandler)
                } else {
                    null
                }
            },
        )
        pipeline.addLast(
            PipelineHandlerNames.HTTP_CODEC,
            CleartextHttp2ServerUpgradeHandler(
                sourceCodec,
                upgradeHandler,
                createPriorKnowledgeHttp2Initializer(channel, scope, cryptoExecutor, connectionCapture),
            ),
        )
        installHttpOneTail(channel, scope, cryptoExecutor, connectionCapture)
    }

    /** Installs one HTTP/1 request path; the same path remains the fallback after TLS ALPN. */
    private fun installHttpOneTail(
        channel: Channel,
        scope: CoroutineScope,
        cryptoExecutor: ThreadPoolExecutor,
        connectionCapture: com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture?,
        streamId: StreamId? = null,
        downstreamProtocol: ApplicationProtocol? = null,
    ) {
        pipelineInitializers.forEach { initializer -> initializer(channel.pipeline()) }
        channel.pipeline().addLast(
            PipelineHandlerNames.PROXY_HANDLER,
            createProxyHandler(
                channel = channel,
                scope = scope,
                cryptoExecutor = cryptoExecutor,
                connectionCapture = connectionCapture,
                streamId = streamId,
                downstreamProtocol = downstreamProtocol,
            ),
        )
    }

    /** Creates an independent child pipeline for one multiplexed HTTP/2 stream. */
    private fun createHttp2StreamInitializer(
        parentChannel: Channel,
        scope: CoroutineScope,
        cryptoExecutor: ThreadPoolExecutor,
        connectionCapture: com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture?,
    ): ChannelInitializer<Channel> = object : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            channel.attr(ProxyChannelAttributes.CONNECTION_CAPTURE).set(connectionCapture)
            channel.attr(ProxyChannelAttributes.HOST).set(parentChannel.attr(ProxyChannelAttributes.HOST).get())
            channel.attr(ProxyChannelAttributes.PORT).set(parentChannel.attr(ProxyChannelAttributes.PORT).get())
            channel.attr(ProxyChannelAttributes.IS_SSL).set(parentChannel.attr(ProxyChannelAttributes.IS_SSL).get())
            channel.pipeline().addLast(
                PipelineHandlerNames.HTTP2_STREAM_CODEC,
                Http2StreamFrameToHttpObjectCodec(true),
            )
            val nativeStreamId = (channel as? Http2StreamChannel)
                ?.stream()
                ?.id()
                ?.takeIf { value -> value >= 0 }
                ?.toLong()
                ?.let(::StreamId)
            channel.attr(ProxyChannelAttributes.STREAM_ID).set(nativeStreamId)
            channel.attr(ProxyChannelAttributes.APPLICATION_PROTOCOL).set(
                ApplicationProtocol.fromToken(ApplicationProtocolNames.HTTP_2),
            )
            installHttpOneTail(
                channel = channel,
                scope = scope,
                cryptoExecutor = cryptoExecutor,
                connectionCapture = connectionCapture,
                streamId = nativeStreamId,
                downstreamProtocol = ApplicationProtocol.fromToken(ApplicationProtocolNames.HTTP_2),
            )
        }
    }

    /** Installs the modern frame-codec plus multiplexer pair selected by an h2c preface. */
    private fun createPriorKnowledgeHttp2Initializer(
        parentChannel: Channel,
        scope: CoroutineScope,
        cryptoExecutor: ThreadPoolExecutor,
        connectionCapture: com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture?,
    ): ChannelInitializer<Channel> = object : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            val components = createHttp2Components(parentChannel, scope, cryptoExecutor, connectionCapture)
            channel.pipeline().addAfter(
                PipelineHandlerNames.HTTP_CODEC,
                PipelineHandlerNames.HTTP2_CODEC,
                components.frameCodec,
            )
            channel.pipeline().addAfter(
                PipelineHandlerNames.HTTP2_CODEC,
                PipelineHandlerNames.HTTP2_MULTIPLEX,
                components.multiplexHandler,
            )
        }
    }

    /** Builds bounded connection-scoped HTTP/2 handlers with one isolated child per stream. */
    private fun createHttp2Components(
        parentChannel: Channel,
        scope: CoroutineScope,
        cryptoExecutor: ThreadPoolExecutor,
        connectionCapture: com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture?,
    ): Http2Components {
        val childInitializer = createHttp2StreamInitializer(
            parentChannel,
            scope,
            cryptoExecutor,
            connectionCapture,
        )
        val settings = Http2Settings()
            .maxConcurrentStreams(runtimePolicy.maximumHttp2ConcurrentStreams)
            .maxHeaderListSize(runtimePolicy.maximumHttp2HeaderListBytes)
            .initialWindowSize(runtimePolicy.http2InitialWindowBytes)
        val frameCodec = Http2FrameCodecBuilder.forServer()
            .initialSettings(settings)
            .gracefulShutdownTimeoutMillis(runtimePolicy.gracefulShutdownTimeoutMillis)
            .build()
        return Http2Components(
            frameCodec = frameCodec,
            multiplexHandler = Http2MultiplexHandler(
                childInitializer,
                createHttp2StreamInitializer(parentChannel, scope, cryptoExecutor, connectionCapture),
            ),
        )
    }

    /** Creates the shared HTTP-object bridge for either a socket or an HTTP/2 stream child. */
    private fun createProxyHandler(
        channel: Channel,
        scope: CoroutineScope,
        cryptoExecutor: ThreadPoolExecutor,
        connectionCapture: com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture?,
        streamId: StreamId?,
        downstreamProtocol: ApplicationProtocol?,
    ): KNetStreamingProxyHandler = KNetStreamingProxyHandler(
        serverTlsContextProvider = serverTlsContextProvider,
        keyManagerProvider = keyManagerProvider,
        strictSsl = verifyUpstreamTls,
        proxyScope = scope,
        runtimePolicy = runtimePolicy,
        admissionController = admissionController,
        certificateExecutor = cryptoExecutor,
        connectionCapture = connectionCapture,
        requiresFullResponseAggregation = requiresFullResponseAggregation,
        streamId = streamId,
        downstreamProtocol = downstreamProtocol,
        httpTwoUpstreamPool = httpTwoUpstreamPool,
        installTlsApplicationProtocol = { pipeline ->
            pipeline.addAfter(
                PipelineHandlerNames.SSL,
                PipelineHandlerNames.ALPN,
                object : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                    override fun configurePipeline(context: io.netty.channel.ChannelHandlerContext, protocol: String) {
                        when (protocol) {
                            ApplicationProtocolNames.HTTP_2 -> {
                                val components = createHttp2Components(
                                    channel,
                                    scope,
                                    cryptoExecutor,
                                    connectionCapture,
                                )
                                context.pipeline().addAfter(
                                    PipelineHandlerNames.ALPN,
                                    PipelineHandlerNames.HTTP2_CODEC,
                                    components.frameCodec,
                                )
                                context.pipeline().addAfter(
                                    PipelineHandlerNames.HTTP2_CODEC,
                                    PipelineHandlerNames.HTTP2_MULTIPLEX,
                                    components.multiplexHandler,
                                )
                            }
                            ApplicationProtocolNames.HTTP_1_1, "" -> context.pipeline().addAfter(
                                PipelineHandlerNames.ALPN,
                                PipelineHandlerNames.HTTP_CODEC,
                                HttpServerCodec(),
                            )
                            else -> throw IllegalStateException("Unsupported negotiated protocol: $protocol")
                        }
                    }
                },
            )
        },
    )

    private data class Http2Components(
        val frameCodec: Http2FrameCodec,
        val multiplexHandler: Http2MultiplexHandler,
    )

    /** Creates the runtime-owned bounded worker used for leaf generation and TLS context assembly. */
    private fun createCertificateExecutor(): ThreadPoolExecutor {
        val threadNumber = AtomicInteger(0)
        return ThreadPoolExecutor(
            CERTIFICATE_WORKERS,
            CERTIFICATE_WORKERS,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(CERTIFICATE_QUEUE_CAPACITY),
            { task ->
                Thread(task, "knet-certificate-${threadNumber.incrementAndGet()}").apply {
                    isDaemon = true
                }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )
    }
}
