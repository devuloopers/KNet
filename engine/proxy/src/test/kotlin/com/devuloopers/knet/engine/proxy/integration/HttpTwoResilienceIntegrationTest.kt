package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider
import com.sun.net.httpserver.HttpServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2PingFrame
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2PingFrame
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
import io.netty.util.ReferenceCountUtil
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-socket resilience qualification for HTTP/2 connection-scoped failures and lifecycle churn.
 *
 * These cases complement the stream/translation suites by proving that malformed control traffic,
 * PING handling, and repeated parent replacement cannot terminate or poison the shared proxy listener.
 */
class HttpTwoResilienceIntegrationTest {

    /** Verifies a protocol error closes only the offending parent and preserves subsequent H2C traffic. */
    @Test
    fun `malformed frame receives goaway without terminating the proxy listener`() {
        val origin = LoopbackOrigin()
        val proxy = createProxy()
        origin.start()
        proxy.start()

        val group = NioEventLoopGroup(1)
        var healthyParent: Channel? = null
        try {
            Socket().use { offender ->
                offender.connect(assertNotNull(proxy.boundAddress()), SOCKET_TIMEOUT_MILLIS)
                offender.soTimeout = SOCKET_TIMEOUT_MILLIS
                offender.getOutputStream().apply {
                    write(HTTP_TWO_CLIENT_PREFACE)
                    write(EMPTY_SETTINGS_FRAME)
                    write(INVALID_STREAM_ZERO_DATA_FRAME)
                    flush()
                }

                assertTrue(
                    offender.getInputStream().containsFrameType(HTTP_TWO_GOAWAY_FRAME_TYPE),
                    "The invalid stream-zero DATA frame must receive a connection-scoped GOAWAY.",
                )
            }

            healthyParent = openH2cParent(group, assertNotNull(proxy.boundAddress()))
            assertEquals(
                "/healthy-after-malformed",
                sendRequest(healthyParent, origin.port, "/healthy-after-malformed")
                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertTrue(healthyParent.isActive, "The proxy listener must admit a replacement HTTP/2 parent.")
        } finally {
            healthyParent?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.close()
        }
    }

    /** Verifies connection-scoped PING acknowledgement and continued stream admission on the same parent. */
    @Test
    fun `ping acknowledgement leaves the parent available for application streams`() {
        val origin = LoopbackOrigin()
        val proxy = createProxy()
        origin.start()
        proxy.start()

        val pingAcknowledgement = CompletableFuture<Long>()
        val group = NioEventLoopGroup(1)
        var parent: Channel? = null
        try {
            parent = openH2cParent(
                group = group,
                address = assertNotNull(proxy.boundAddress()),
                onPingAcknowledgement = { payload -> pingAcknowledgement.complete(payload) },
            )
            parent.writeAndFlush(DefaultHttp2PingFrame(PING_PAYLOAD)).syncUninterruptibly()

            assertEquals(
                PING_PAYLOAD,
                pingAcknowledgement.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertEquals(
                "/after-ping",
                sendRequest(parent, origin.port, "/after-ping").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertTrue(parent.isActive)
        } finally {
            parent?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.close()
        }
    }

    /** Verifies repeated H2C parent creation and teardown does not degrade the shared listener. */
    @Test
    fun `repeated http two connection churn preserves forwarding`() {
        val origin = LoopbackOrigin()
        val proxy = createProxy()
        origin.start()
        proxy.start()

        val group = NioEventLoopGroup(2)
        var finalParent: Channel? = null
        try {
            repeat(CHURN_CONNECTIONS) { index ->
                val parent = openH2cParent(group, assertNotNull(proxy.boundAddress()))
                try {
                    val path = "/churn-$index"
                    assertEquals(
                        path,
                        sendRequest(parent, origin.port, path).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    )
                } finally {
                    parent.close().syncUninterruptibly()
                }
            }

            finalParent = openH2cParent(group, assertNotNull(proxy.boundAddress()))
            assertEquals(
                "/after-churn",
                sendRequest(finalParent, origin.port, "/after-churn")
                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            assertTrue(finalParent.isActive)
        } finally {
            finalParent?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            proxy.stop()
            origin.close()
        }
    }

    /** Creates a production-configured proxy with ephemeral loopback ownership. */
    private fun createProxy(): KNetProxyServer = KNetProxyServer(
        port = availableLoopbackPort(),
        serverTlsContextProvider = TestServerTlsContextProvider(
            CertificateAuthority.generate(commonName = "KNet HTTP/2 resilience CA"),
            CertificateCache(),
        ),
    )

    /** Opens one Netty HTTP/2 parent using clear-text prior knowledge. */
    private fun openH2cParent(
        group: NioEventLoopGroup,
        address: InetSocketAddress,
        onPingAcknowledgement: ((Long) -> Unit)? = null,
    ): Channel = Bootstrap()
        .group(group)
        .channel(NioSocketChannel::class.java)
        .handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(channel: SocketChannel) {
                channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                if (onPingAcknowledgement != null) {
                    channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                        override fun channelRead(context: ChannelHandlerContext, message: Any) {
                            if (message is Http2PingFrame && message.ack()) {
                                onPingAcknowledgement(message.content())
                                ReferenceCountUtil.release(message)
                            } else {
                                context.fireChannelRead(message)
                            }
                        }
                    })
                }
                channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
            }
        })
        .connect(address)
        .syncUninterruptibly()
        .channel()

    /** Sends one absolute-form proxy request on an isolated HTTP/2 stream. */
    private fun sendRequest(parent: Channel, originPort: Int, path: String): CompletableFuture<String> {
        val response = CompletableFuture<String>()
        val stream = Http2StreamChannelBootstrap(parent)
            .handler(responseInitializer(response))
            .open()
            .syncUninterruptibly()
            .getNow()
        val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort"
        val request = DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "http://$authority$path",
        )
        request.headers().set(HttpHeaderNames.HOST, authority)
        stream.writeAndFlush(request).addListener { write ->
            if (!write.isSuccess) response.completeExceptionally(write.cause())
        }
        return response
    }

    /** Converts one response stream into a bounded UTF-8 result used by assertions. */
    private fun responseInitializer(response: CompletableFuture<String>): ChannelInitializer<Channel> =
        object : ChannelInitializer<Channel>() {
            override fun initChannel(channel: Channel) {
                channel.pipeline().addLast(Http2StreamFrameToHttpObjectCodec(false))
                channel.pipeline().addLast(HttpObjectAggregator(MAXIMUM_TEST_RESPONSE_BYTES))
                channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(context: ChannelHandlerContext, message: Any) {
                        if (message is FullHttpResponse) {
                            response.complete(message.content().toString(Charsets.UTF_8))
                            ReferenceCountUtil.release(message)
                            context.close()
                        } else {
                            context.fireChannelRead(message)
                        }
                    }

                    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
                        response.completeExceptionally(cause)
                        context.close()
                    }

                    override fun channelInactive(context: ChannelHandlerContext) {
                        response.completeExceptionally(
                            IllegalStateException("HTTP/2 stream closed before a complete response."),
                        )
                        context.fireChannelInactive()
                    }
                })
            }
        }

    /** Returns true when the bounded server response contains the requested HTTP/2 frame type. */
    private fun InputStream.containsFrameType(expectedType: Int): Boolean {
        repeat(MAXIMUM_CONTROL_FRAMES_TO_READ) {
            val header = readExact(HTTP_TWO_FRAME_HEADER_BYTES) ?: return false
            val payloadLength =
                ((header[0].toInt() and 0xff) shl 16) or
                    ((header[1].toInt() and 0xff) shl 8) or
                    (header[2].toInt() and 0xff)
            val type = header[3].toInt() and 0xff
            if (readExact(payloadLength) == null) return false
            if (type == expectedType) return true
        }
        return false
    }

    /** Reads exactly [byteCount] bytes or returns null when the peer closes first. */
    private fun InputStream.readExact(byteCount: Int): ByteArray? {
        val result = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val readBytes = read(result, offset, byteCount - offset)
            if (readBytes < 0) return null
            offset += readBytes
        }
        return result
    }

    /** Small concurrent origin that echoes each request path. */
    private class LoopbackOrigin : AutoCloseable {
        private val executor = Executors.newCachedThreadPool()
        private val server = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            this.executor = this@LoopbackOrigin.executor
            createContext("/") { exchange ->
                val body = exchange.requestURI.path.encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { output -> output.write(body) }
            }
        }

        val port: Int get() = server.address.port

        fun start() = server.start()

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    /** Releases unexpected remotely initiated streams without retaining their frames. */
    private class DiscardInboundStreamHandler : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            ReferenceCountUtil.release(message)
        }
    }

    /** Reserves and releases an ephemeral loopback port for the proxy listener. */
    private fun availableLoopbackPort(): Int = ServerSocket(0, 1).use { socket -> socket.localPort }

    private companion object {
        private const val SOCKET_TIMEOUT_MILLIS = 5_000
        private const val REQUEST_TIMEOUT_SECONDS = 10L
        private const val MAXIMUM_TEST_RESPONSE_BYTES = 64 * 1024
        private const val MAXIMUM_CONTROL_FRAMES_TO_READ = 8
        private const val HTTP_TWO_FRAME_HEADER_BYTES = 9
        private const val HTTP_TWO_GOAWAY_FRAME_TYPE = 7
        private const val PING_PAYLOAD = 0x4b4e455448545032L
        private const val CHURN_CONNECTIONS = 40

        private val HTTP_TWO_CLIENT_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray(Charsets.US_ASCII)
        private val EMPTY_SETTINGS_FRAME = byteArrayOf(0, 0, 0, 4, 0, 0, 0, 0, 0)
        private val INVALID_STREAM_ZERO_DATA_FRAME = byteArrayOf(0, 0, 0, 0, 1, 0, 0, 0, 0)
    }
}
