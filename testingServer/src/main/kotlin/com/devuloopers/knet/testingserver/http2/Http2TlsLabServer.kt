package com.devuloopers.knet.testingserver.http2

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.util.SelfSignedCertificate
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns a real TLS + ALPN HTTP/2 listener for stream-level proxy qualification.
 *
 * This listener is deliberately separate from WebFlux. The ordinary lab remains useful for HTTP/1.1 and H2C,
 * while this transport can emit HTTP/2-only frames such as trailing headers, RST_STREAM, and GOAWAY without
 * leaking fixture behavior into production modules.
 */
@Component
class Http2TlsLabServer(
    private val properties: Http2TlsLabProperties,
) : SmartLifecycle {
    // This listener writes directly to Netty and deliberately owns its Jackson 2 mapper. Spring Boot 4 uses
    // Jackson 3 for WebFlux and therefore no longer publishes a com.fasterxml ObjectMapper bean.
    private val objectMapper = ObjectMapper()

    @Volatile
    private var serverChannel: Channel? = null

    @Volatile
    private var running = false

    private var acceptorGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var certificate: SelfSignedCertificate? = null
    private val acceptedConnections = AtomicInteger(0)

    /** Actual listener port, including the operating-system-selected value when configured with zero. */
    val boundPort: Int
        get() = (serverChannel?.localAddress() as? InetSocketAddress)?.port ?: properties.port

    /** Public PEM certificate used by this process, suitable for an explicitly trusted local test profile. */
    val certificatePem: ByteArray
        get() = checkNotNull(certificate) { "The HTTP/2 TLS lab is not running." }.certificate().readBytes()

    /** Number of TCP clients admitted since the current listener start, used by pooling qualification. */
    val acceptedConnectionCount: Int
        get() = acceptedConnections.get()

    /** Starts the ALPN listener and publishes it only after the TCP bind succeeds. */
    override fun start() {
        if (running) return

        acceptedConnections.set(0)
        val newCertificate = SelfSignedCertificate("localhost")
        val sslContext = createHttp2SslContext(newCertificate)
        val newAcceptorGroup = NioEventLoopGroup(1)
        val newWorkerGroup = NioEventLoopGroup()

        try {
            val channel = ServerBootstrap()
                .group(newAcceptorGroup, newWorkerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(http2ChannelInitializer(sslContext))
                .bind(properties.host, properties.port)
                .syncUninterruptibly()
                .channel()

            certificate = newCertificate
            acceptorGroup = newAcceptorGroup
            workerGroup = newWorkerGroup
            serverChannel = channel
            running = true
        } catch (throwable: Throwable) {
            newAcceptorGroup.shutdownGracefully().syncUninterruptibly()
            newWorkerGroup.shutdownGracefully().syncUninterruptibly()
            newCertificate.delete()
            throw throwable
        }
    }

    /** Stops accepting connections and releases every listener-owned event-loop thread. */
    override fun stop() {
        if (!running) return
        running = false
        serverChannel?.close()?.syncUninterruptibly()
        serverChannel = null
        acceptorGroup?.shutdownGracefully()?.syncUninterruptibly()
        workerGroup?.shutdownGracefully()?.syncUninterruptibly()
        acceptorGroup = null
        workerGroup = null
        certificate?.delete()
        certificate = null
    }

    /** Stops the listener before reporting lifecycle completion to Spring. */
    override fun stop(callback: Runnable) {
        stop()
        callback.run()
    }

    override fun isRunning(): Boolean = running

    override fun isAutoStartup(): Boolean = true

    private fun http2ChannelInitializer(sslContext: SslContext): ChannelInitializer<SocketChannel> =
        object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(channel: SocketChannel) {
                acceptedConnections.incrementAndGet()
                channel.pipeline()
                    .addLast(sslContext.newHandler(channel.alloc()))
                    .addLast(
                        object : ApplicationProtocolNegotiationHandler(UNSUPPORTED_PROTOCOL) {
                            override fun configurePipeline(context: ChannelHandlerContext, protocol: String) {
                                require(protocol == ApplicationProtocolNames.HTTP_2) {
                                    "The HTTP/2 TLS lab requires ALPN protocol h2, received '$protocol'."
                                }
                                context.pipeline()
                                    .addLast(Http2FrameCodecBuilder.forServer().build())
                                    .addLast(
                                        Http2MultiplexHandler(
                                            object : ChannelInitializer<Http2StreamChannel>() {
                                                override fun initChannel(streamChannel: Http2StreamChannel) {
                                                    streamChannel.pipeline().addLast(
                                                        Http2LabStreamHandler(objectMapper),
                                                    )
                                                }
                                            },
                                        ),
                                    )
                            }
                        },
                    )
            }
        }

    private fun createHttp2SslContext(certificate: SelfSignedCertificate): SslContext =
        SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
            .sslProvider(SslProvider.JDK)
            .applicationProtocolConfig(
                ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                ),
            )
            .build()

    private companion object {
        const val UNSUPPORTED_PROTOCOL = "unsupported"
    }
}
