package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.tls.*
import com.devuloopers.knet.engine.simulator.NetworkConditionChannelHandler
import com.devuloopers.knet.engine.simulator.NetworkConditionEngine
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.*
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.test.*

/** Negotiated ALPN and real multiplexed payload prove h2 passthrough, rather than an advertised ClientHello token. */
class ProtectedHttpTwoTunnelIntegrationTest {
    @Test fun `origin HTTP2 certificate and concurrent payload survive one shared 100 kbps opaque tunnel`() {
        val authority = CertificateAuthority.generate(commonName = "Protected h2 origin")
        val leaf = CertificateCache().get("localhost", authority)
        val payload = ByteArray(12_500) { (it % 127).toByte() }
        val originRequests = AtomicInteger()
        val negotiated = AtomicInteger()
        val certificateRequests = AtomicInteger()
        val acceptor = NioEventLoopGroup(1)
        val worker = NioEventLoopGroup(1)
        val tls = SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate, authority.certificate)
            .sslProvider(SslProvider.JDK)
            .applicationProtocolConfig(ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, "h2"))
            .build()
        val origin = ServerBootstrap().group(acceptor, worker).channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline().addLast(tls.newHandler(channel.alloc()))
                    channel.pipeline().addLast(object : ApplicationProtocolNegotiationHandler("unsupported") {
                        override fun configurePipeline(context: ChannelHandlerContext, protocol: String) {
                            check(protocol == "h2")
                            negotiated.incrementAndGet()
                            context.pipeline().addLast(Http2FrameCodecBuilder.forServer().build())
                            context.pipeline().addLast(Http2MultiplexHandler(object : ChannelInitializer<Channel>() {
                                override fun initChannel(stream: Channel) {
                                    stream.pipeline().addLast(object : SimpleChannelInboundHandler<Http2StreamFrame>() {
                                        override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
                                            if (frame is Http2HeadersFrame && frame.isEndStream) {
                                                originRequests.incrementAndGet()
                                                context.write(DefaultHttp2HeadersFrame(DefaultHttp2Headers().status("200")
                                                    .set("content-length", payload.size.toString()), false))
                                                context.write(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(payload), false))
                                                context.writeAndFlush(DefaultHttp2HeadersFrame(DefaultHttp2Headers()
                                                    .set("fixture-trailer", "finished"), true))
                                            }
                                        }
                                    })
                                }
                            }))
                        }
                    })
                }
            }).bind("127.0.0.1", 0).syncUninterruptibly().channel()
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration(enabled = true,
            globalProfileId = NetworkConditionBuiltIns.STREAMING_100_KBPS.id) })
        val proxy = KNetProxyServer(port = ServerSocket(0).use { it.localPort },
            serverTlsContextProvider = ServerTlsContextProvider { _, _ ->
                certificateRequests.incrementAndGet()
                CompletableFuture.failedFuture(AssertionError("Protected h2 must keep origin TLS"))
            },
            pipelineInitializers = listOf { it.addLast("h2Conditions", NetworkConditionChannelHandler(engine)) },
            tlsInterceptionPolicy = TlsInterceptionPolicy { TlsInterceptionDecision(TlsInterceptionMode.TUNNEL) })
        proxy.start()
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null); setCertificateEntry("origin", authority.certificate)
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        val client = HttpClient.newBuilder().sslContext(ssl).proxy(ProxySelector.of(proxy.boundAddress()))
            .connectTimeout(10.seconds.toJavaDuration()).version(HttpClient.Version.HTTP_2).build()
        val endpoint = URI("https://localhost:${(origin.localAddress() as InetSocketAddress).port}/protected")
        fun request() = HttpRequest.newBuilder(endpoint).timeout(20.seconds.toJavaDuration()).build()
        try {
            val warm = client.send(request(), HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(HttpClient.Version.HTTP_2, warm.version())
            assertContentEquals(payload, warm.body())
            assertContentEquals(leaf.certificate.encoded, warm.sslSession().orElseThrow().peerCertificates.first().encoded)
            val started = System.nanoTime()
            val concurrent = (0 until 3).map { client.sendAsync(request(), HttpResponse.BodyHandlers.ofByteArray()) }
            concurrent.forEach { future ->
                val response = future.get(15, TimeUnit.SECONDS)
                assertEquals(HttpClient.Version.HTTP_2, response.version())
                assertContentEquals(payload, response.body())
            }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 2_500,
                "Multiplexed protected streams received separate or bypassed rate budgets")
            assertEquals(4, originRequests.get(), "The relay must never replay application requests")
            assertEquals(1, negotiated.get(), "All four requests must use the same negotiated h2 tunnel")
            assertEquals(0, certificateRequests.get())
        } finally {
            proxy.stop(); client.close(); origin.close().syncUninterruptibly()
            acceptor.shutdownGracefully(0, 3, TimeUnit.SECONDS).syncUninterruptibly()
            worker.shutdownGracefully(0, 3, TimeUnit.SECONDS).syncUninterruptibly()
        }
    }
}
