package com.devuloopers.knet.engine.proxy

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.handler.KNetProxyHandler
import com.devuloopers.knet.domain.network.model.ProxyTrafficListener
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
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
    private val listener: ProxyTrafficListener? = null
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

        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .handler(LoggingHandler(LogLevel.DEBUG))
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()

                    pipeline.addLast("httpCodec", HttpServerCodec())
                    pipeline.addLast("httpAggregator", HttpObjectAggregator(10 * 1024 * 1024))

                    pipelineInitializers.forEach { it(pipeline) }

                    pipeline.addLast("proxyHandler", KNetProxyHandler(ca, certCache, listener))
                }
            })

        val channelFuture = bootstrap.bind(InetSocketAddress(port)).sync()
        serverChannel = channelFuture.channel()
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

        serverChannel?.close()?.syncUninterruptibly()
        serverChannel = null

        bossGroup?.shutdownGracefully()
        workerGroup?.shutdownGracefully()
        bossGroup = null
        workerGroup = null
    }

    /**
     * Returns true if the proxy server is currently running and listening for traffic.
     */
    fun isRunning(): Boolean = isStarted.get()
}
