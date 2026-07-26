package com.devuloopers.knet.engine

import com.devuloopers.knet.crypto.CertificateAuthority
import com.devuloopers.knet.crypto.CertificateCache
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
import com.devuloopers.knet.model.ProxyTrafficListener
import java.net.InetSocketAddress
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
        val pipelineInitializers = java.util.concurrent.CopyOnWriteArrayList<(io.netty.channel.ChannelPipeline) -> Unit>()
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

        // bossGroup accepts incoming connections. workerGroup handles execution of connection channels.
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .handler(LoggingHandler(LogLevel.DEBUG))
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()

                    // Add HTTP Server codecs to decode requests and encode responses.
                    pipeline.addLast("httpCodec", HttpServerCodec())
                    
                    // Aggregate HTTP fragments (headers, chunks) into single FullHttpRequest objects.
                    // Max aggregation size set to 10MB to handle standard APIs and large headers.
                    pipeline.addLast("httpAggregator", HttpObjectAggregator(10 * 1024 * 1024))
                    
                    // Run dynamically registered initializers (e.g. KNetInterceptorHandler)
                    pipelineInitializers.forEach { it(pipeline) }

                    // Add KNet's core proxy logic handler.
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

        try {
            serverChannel?.close()?.sync()
        } catch (e: Exception) {
            // Ignore channel closure errors
        } finally {
            serverChannel = null
        }

        // Gracefully shutdown event loop execution threads.
        bossGroup?.shutdownGracefully()
        workerGroup?.shutdownGracefully()
        
        bossGroup = null
        workerGroup = null
    }

    /**
     * Returns whether the server is currently running.
     *
     * @return True if started and running.
     */
    fun isRunning(): Boolean {
        return isStarted.get()
    }
}
