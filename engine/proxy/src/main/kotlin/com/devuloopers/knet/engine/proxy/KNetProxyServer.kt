package com.devuloopers.knet.engine.proxy

import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.handler.KNetProxyHandler
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
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
import io.netty.util.ResourceLeakDetector
import io.netty.util.concurrent.GlobalEventExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Netty-based asynchronous proxy server that listens on a port, intercepts HTTP/HTTPS requests,
 * and routes them through the interception pipeline.
 *
 * @property port The port KNet will bind to (default: 8080).
 * @property ca The Root Certificate Authority used to dynamically sign leaf certificates for SSL decryption.
 * @property certCache The cache managing generated leaf certificates.
 * @property listener The listener to receive captured HTTP requests and responses.
 */
class KNetProxyServer(
    val port: Int = 8080,
    private val ca: CertificateAuthority,
    private val certCache: CertificateCache,
    private val listener: ProxyTrafficListener? = null,
    private val keyManagerProvider: com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider? = null
) {

    companion object {
        /**
         * List of dynamically registered initializers to modify the Netty channel pipeline.
         * Run before the default proxy logic handler.
         */
        val pipelineInitializers = CopyOnWriteArrayList<(io.netty.channel.ChannelPipeline) -> Unit>()
    }

    private val isStarted = AtomicBoolean(false)
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    private var serverScope: kotlinx.coroutines.CoroutineScope? = null
    private val activeChannels: ChannelGroup = DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

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

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        serverScope = scope

        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .handler(LoggingHandler(LogLevel.DEBUG))
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    activeChannels.add(ch)
                    val pipeline = ch.pipeline()

                    pipeline.addLast(PipelineHandlerNames.HTTP_CODEC, HttpServerCodec())
                    pipeline.addLast(
                        PipelineHandlerNames.HTTP_AGGREGATOR,
                        HttpObjectAggregator(PipelineHandlerNames.MAX_CONTENT_LENGTH_BYTES)
                    )

                    pipelineInitializers.forEach { it(pipeline) }

                    pipeline.addLast(
                        PipelineHandlerNames.PROXY_HANDLER,
                        KNetProxyHandler(ca, certCache, listener, keyManagerProvider, proxyScope = scope)
                    )
                }
            })

        val channelFuture = bootstrap.bind(InetSocketAddress("0.0.0.0", port)).sync()
        serverChannel = channelFuture.channel()
    }

    /**
     * Flushes and closes all active client channels connected to the proxy.
     * Called when host network interface switches occur to prevent stale socket connections.
     */
    fun flushActiveChannels() {
        activeChannels.close()
    }

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


        flushActiveChannels()
        serverChannel?.close()?.syncUninterruptibly()
        serverChannel = null

        bossGroup?.shutdownGracefully(100, 2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        workerGroup?.shutdownGracefully(100, 2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        bossGroup = null
        workerGroup = null
    }


    /**
     * Returns true if the proxy server is currently running and listening for traffic.
     */
    fun isRunning(): Boolean = isStarted.get()
}
