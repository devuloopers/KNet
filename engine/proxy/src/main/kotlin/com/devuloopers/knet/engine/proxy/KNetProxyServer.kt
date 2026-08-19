package com.devuloopers.knet.engine.proxy

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.handler.KNetProxyHandler
import com.devuloopers.knet.engine.proxy.handler.KNetStreamingProxyHandler
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TrafficEndpoint
import com.devuloopers.knet.traffic.model.IngressAttributionLookup
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
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.ResourceLeakDetector
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


/**
 * Netty-based asynchronous proxy server that listens on a port, intercepts HTTP/HTTPS requests,
 * and routes them through the interception pipeline.
 *
 * @property bindHost Explicit listener host. The safe default is IPv4 loopback.
 * @property port The port KNet will bind to (default: 8080).
 * @property ca The Root Certificate Authority used to dynamically sign leaf certificates for SSL decryption.
 * @property certCache The cache managing generated leaf certificates.
 * @property keyManagerProvider Optional upstream client-identity selector for mTLS.
 * @property verifyUpstreamTls Whether upstream server certificates must be verified.
 * @property runtimePolicy Enforced connection and timeout limits for this runtime.
 * @property pipelineInitializers Instance-owned pipeline extensions installed by composition.
 * @property requiresFullResponseAggregation Breakpoint capability decision supplied by composition
 * for features that require a bounded [io.netty.handler.codec.http.FullHttpResponse]. Normal
 * traffic must leave this false so upstream responses retain streaming backpressure.
 * @property requiresFullRequestAggregation Breakpoint capability decision supplied by composition
 * for features that require a bounded [io.netty.handler.codec.http.FullHttpRequest]. When false,
 * the HTTP/1 transport streams uploads directly to the origin.
 * @property runtimeMetrics Runtime-owned constant-time operational metrics sink.
 */
class KNetProxyServer(
    val bindHost: String = DEFAULT_BIND_HOST,
    val port: Int = 8080,
    private val ca: CertificateAuthority,
    private val certCache: CertificateCache,
    private val keyManagerProvider: com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider? = null,
    private val verifyUpstreamTls: Boolean = true,
    private val runtimePolicy: KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(),
    private val pipelineInitializers: List<(io.netty.channel.ChannelPipeline) -> Unit> = emptyList(),
    private val captureSink: ProxyCaptureSink? = null,
    private val ingressContext: IngressContext = IngressContext(IngressKind.Local),
    private val ingressAttribution: IngressAttributionLookup? = null,
    private val requiresFullResponseAggregation: () -> Boolean = { false },
    private val requiresFullRequestAggregation: () -> Boolean = { false },
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
                        pipeline.addLast(PipelineHandlerNames.HTTP_CODEC, HttpServerCodec())
                        val aggregateRequest = requiresFullRequestAggregation()
                        if (aggregateRequest) {
                            pipeline.addLast(
                                PipelineHandlerNames.HTTP_AGGREGATOR,
                                HttpObjectAggregator(PipelineHandlerNames.MAX_CONTENT_LENGTH_BYTES),
                            )
                        }

                        pipelineInitializers.forEach { it(pipeline) }

                        val proxyHandler = if (aggregateRequest) {
                            KNetProxyHandler(
                                ca = ca,
                                certCache = certCache,
                                keyManagerProvider = keyManagerProvider,
                                strictSsl = verifyUpstreamTls,
                                proxyScope = scope,
                                runtimePolicy = runtimePolicy,
                                admissionController = admissionController,
                                certificateExecutor = cryptoExecutor,
                                connectionCapture = connectionCapture,
                                requiresFullResponseAggregation = requiresFullResponseAggregation,
                            )
                        } else {
                            KNetStreamingProxyHandler(
                                ca = ca,
                                certCache = certCache,
                                keyManagerProvider = keyManagerProvider,
                                strictSsl = verifyUpstreamTls,
                                proxyScope = scope,
                                runtimePolicy = runtimePolicy,
                                admissionController = admissionController,
                                certificateExecutor = cryptoExecutor,
                                connectionCapture = connectionCapture,
                                requiresFullResponseAggregation = requiresFullResponseAggregation,
                            )
                        }
                        pipeline.addLast(PipelineHandlerNames.PROXY_HANDLER, proxyHandler)
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
     * Connectivity adapters use this boundary after a network transition, and runtime composition
     * uses it when a per-connection capability such as breakpoint aggregation changes.
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
