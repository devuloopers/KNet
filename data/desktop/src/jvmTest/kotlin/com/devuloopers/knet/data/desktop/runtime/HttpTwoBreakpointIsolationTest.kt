package com.devuloopers.knet.data.desktop.runtime

import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.breakpoint.BreakpointBody
import com.devuloopers.knet.application.port.breakpoint.BreakpointBodyEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.port.breakpoint.BreakpointRequestEdit
import com.devuloopers.knet.application.port.breakpoint.BreakpointResponseEdit
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.capture.ProxyBodyReservation
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureConnectionMetadata
import com.devuloopers.knet.engine.proxy.capture.ProxyCaptureSink
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.id.StreamId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.ExchangeTimings
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficOrigin
import com.devuloopers.knet.traffic.model.body.ContentEncoding
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import com.devuloopers.knet.traffic.model.http.ResponseHead
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
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec
import io.netty.util.ReferenceCountUtil
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real runtime proof that an HTTP/2 breakpoint suspends only its selected stream channel. */
class HttpTwoBreakpointIsolationTest {

    @Test
    fun `request edit and drop affect only their selected http two streams`() = runBlocking {
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestURI.path.encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val coordinator = BreakpointCoordinator().also { breakpointCoordinator ->
            breakpointCoordinator.replaceRules(
                listOf(
                    BreakpointRule(id = "http2-edit", phase = BreakpointPhase.REQUEST, urlPattern = "*/edit"),
                    BreakpointRule(id = "http2-drop", phase = BreakpointPhase.REQUEST, urlPattern = "*/drop"),
                ),
            )
        }
        val runtime = ProxyRuntimeRepository(
            certificateAuthority = CertificateAuthority.generate(),
            certificateCache = CertificateCache(),
            breakpointGate = coordinator,
        )
        val proxyPort = availableLoopbackPort()
        runtime.startProxy(port = proxyPort, captureSink = ProxyCaptureSink { null })

        val group = NioEventLoopGroup(1)
        var parent: Channel? = null
        try {
            parent = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort)
                .syncUninterruptibly()
                .channel()

            val edited = send(parent, origin.address.port, "/edit")
            val editPending = awaitPending(coordinator)
            val originalTarget = assertIs<RequestTarget.Absolute>(editPending.candidate.request.head.target)
            val replacement = editPending.candidate.request.copy(
                head = editPending.candidate.request.head.copy(
                    target = originalTarget.copy(pathAndQuery = "/edited"),
                ),
            )
            assertTrue(
                coordinator.resolve(
                    editPending.id,
                    BreakpointDecision.ResumeRequest(BreakpointRequestEdit(replacement)),
                ),
            )
            assertEquals("/edited", edited.get(10, TimeUnit.SECONDS))

            val dropped = send(parent, origin.address.port, "/drop")
            val dropPending = awaitPending(coordinator)
            assertTrue(coordinator.resolve(dropPending.id, BreakpointDecision.Drop))
            assertFailsWith<ExecutionException> { dropped.get(10, TimeUnit.SECONDS) }

            assertEquals("/sibling", send(parent, origin.address.port, "/sibling").get(10, TimeUnit.SECONDS))
            assertTrue(parent.isActive, "Dropping one HTTP/2 exchange must not close its parent connection.")
        } finally {
            parent?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            runtime.close()
            origin.stop(0)
        }
    }

    @Test
    fun `response edit and drop affect only their selected http two streams`() = runBlocking {
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/") { exchange ->
                val body = "origin:${exchange.requestURI.path}".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val coordinator = BreakpointCoordinator().also { breakpointCoordinator ->
            breakpointCoordinator.replaceRules(
                listOf(
                    BreakpointRule(
                        id = "http2-response-edit",
                        phase = BreakpointPhase.RESPONSE,
                        urlPattern = "*/edit-response",
                    ),
                    BreakpointRule(
                        id = "http2-response-drop",
                        phase = BreakpointPhase.RESPONSE,
                        urlPattern = "*/drop-response",
                    ),
                ),
            )
        }
        val runtime = ProxyRuntimeRepository(
            certificateAuthority = CertificateAuthority.generate(),
            certificateCache = CertificateCache(),
            breakpointGate = coordinator,
        )
        val proxyPort = availableLoopbackPort()
        runtime.startProxy(port = proxyPort, captureSink = ProxyCaptureSink { null })

        val group = NioEventLoopGroup(1)
        var parent: Channel? = null
        try {
            parent = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort)
                .syncUninterruptibly()
                .channel()

            val edited = send(parent, origin.address.port, "/edit-response")
            val editPending = awaitPending(coordinator)
            val replacementBody = BreakpointBody("edited-response".encodeToByteArray())
            assertTrue(
                coordinator.resolve(
                    editPending.id,
                    BreakpointDecision.ResumeResponse(
                        BreakpointResponseEdit(
                            response = requireNotNull(editPending.candidate.response),
                            body = BreakpointBodyEdit.Replace(replacementBody),
                        ),
                    ),
                ),
            )
            assertEquals("edited-response", edited.get(10, TimeUnit.SECONDS))

            val dropped = send(parent, origin.address.port, "/drop-response")
            val dropPending = awaitPending(coordinator)
            assertTrue(coordinator.resolve(dropPending.id, BreakpointDecision.Drop))
            assertFailsWith<ExecutionException> { dropped.get(10, TimeUnit.SECONDS) }

            assertEquals(
                "origin:/sibling-after-response-drop",
                send(parent, origin.address.port, "/sibling-after-response-drop").get(10, TimeUnit.SECONDS),
            )
            assertTrue(parent.isActive, "Dropping one HTTP/2 response must not close its parent connection.")
        } finally {
            parent?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            runtime.close()
            origin.stop(0)
        }
    }

    @Test
    fun `one paused request does not delay nineteen sibling streams`() =
        verifyStreamIsolation(BreakpointPhase.REQUEST)

    @Test
    fun `one paused response does not delay nineteen sibling streams`() =
        verifyStreamIsolation(BreakpointPhase.RESPONSE)

    private fun verifyStreamIsolation(phase: BreakpointPhase) = runBlocking {
        val origin = HttpServer.create(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestURI.path.encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val coordinator = BreakpointCoordinator().also { breakpointCoordinator ->
            breakpointCoordinator.replaceRules(
                listOf(
                    BreakpointRule(
                        id = "http2-paused-stream",
                        phase = phase,
                        urlPattern = "*/paused",
                    ),
                ),
            )
        }
        val capture = RecordingCaptureSink()
        val runtime = ProxyRuntimeRepository(
            certificateAuthority = CertificateAuthority.generate(),
            certificateCache = CertificateCache(),
            breakpointGate = coordinator,
        )
        val proxyPort = availableLoopbackPort()
        runtime.startProxy(port = proxyPort, captureSink = capture)

        val group = NioEventLoopGroup(1)
        var parent: Channel? = null
        try {
            parent = Bootstrap()
                .group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(channel: SocketChannel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forClient().build())
                        channel.pipeline().addLast(Http2MultiplexHandler(DiscardInboundStreamHandler()))
                    }
                })
                .connect(KNetProxyServer.DEFAULT_BIND_HOST, proxyPort)
                .syncUninterruptibly()
                .channel()

            val paused = send(parent, origin.address.port, "/paused")
            val pending = awaitPending(coordinator)
            assertEquals(phase, pending.candidate.phase)
            assertEquals("HTTP/2", pending.candidate.request.head.protocol.token)

            val siblings = (1..19).map { index ->
                send(parent, origin.address.port, "/open-$index")
            }
            val siblingBodies = siblings.map { future -> future.get(10, TimeUnit.SECONDS) }

            assertEquals((1..19).map { "/open-$it" }.toSet(), siblingBodies.toSet())
            assertFalse(paused.isDone, "The selected stream must remain paused until the user resolves it.")
            assertTrue(coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged))
            assertEquals("/paused", paused.get(10, TimeUnit.SECONDS))

            awaitCondition { capture.started.size == 20 }
            val pausedCapture = capture.started.single { started ->
                (started.request.target as? RequestTarget.Absolute)?.pathAndQuery == "/paused"
            }
            assertNotNull(pausedCapture.streamId)
            assertEquals("HTTP/2", pausedCapture.request.protocol.token)
            assertEquals(20, capture.started.mapNotNull(StartedExchange::streamId).distinct().size)
        } finally {
            parent?.close()?.syncUninterruptibly()
            group.shutdownGracefully().syncUninterruptibly()
            runtime.close()
            origin.stop(0)
        }
    }

    private suspend fun awaitPending(coordinator: BreakpointCoordinator) =
        kotlinx.coroutines.withTimeout(10_000L) {
            coordinator.pendingBreakpoints.value.firstOrNull()
                ?: coordinator.pendingBreakpoints.let { flow ->
                    flow.first { pending -> pending.isNotEmpty() }.single()
                }
        }

    private fun send(parent: Channel, originPort: Int, path: String): CompletableFuture<String> {
        val response = CompletableFuture<String>()
        val stream = Http2StreamChannelBootstrap(parent)
            .handler(object : ChannelInitializer<Channel>() {
                override fun initChannel(channel: Channel) {
                    channel.pipeline().addLast(Http2StreamFrameToHttpObjectCodec(false))
                    channel.pipeline().addLast(HttpObjectAggregator(1024 * 1024))
                    channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                        override fun channelRead(context: ChannelHandlerContext, message: Any) {
                            if (message is FullHttpResponse) {
                                response.complete(message.content().toString(Charsets.UTF_8))
                                message.release()
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
            })
            .open()
            .syncUninterruptibly()
            .getNow()
        val absoluteUrl = "http://${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort$path"
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, absoluteUrl)
        request.headers().set(HttpHeaderNames.HOST, "${KNetProxyServer.DEFAULT_BIND_HOST}:$originPort")
        stream.writeAndFlush(request).addListener { write ->
            if (!write.isSuccess) response.completeExceptionally(write.cause())
        }
        return response
    }

    private fun awaitCondition(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(10L)
        }
        error("Timed out waiting for HTTP/2 breakpoint capture events.")
    }

    private fun availableLoopbackPort(): Int = ServerSocket(0, 1).use { it.localPort }

    private data class StartedExchange(
        val request: RequestHead,
        val streamId: StreamId?,
    )

    private class RecordingCaptureSink : ProxyCaptureSink {
        val started = CopyOnWriteArrayList<StartedExchange>()

        override fun openConnection(metadata: ProxyCaptureConnectionMetadata): ProxyConnectionCapture =
            object : ProxyConnectionCapture {
                override fun startExchange(
                    exchangeId: ExchangeId,
                    request: RequestHead,
                    occurredAtEpochMillis: Long,
                    origin: TrafficOrigin,
                    streamId: StreamId?,
                ): ProxyExchangeCapture {
                    started += StartedExchange(request, streamId)
                    return NoOpExchangeCapture(exchangeId)
                }

                override fun close(errorCode: String?) = Unit
            }
    }

    private class NoOpExchangeCapture(override val exchangeId: ExchangeId) : ProxyExchangeCapture {
        override fun tryReserveBody(
            direction: TrafficDirection,
            contentEncoding: ContentEncoding?,
            requestedBytes: Int,
        ): ProxyBodyReservation? = null

        override fun completeBody(direction: TrafficDirection, observedBytes: Long, occurredAtEpochMillis: Long) = Unit

        override fun cancelBody(
            direction: TrafficDirection,
            observedBytes: Long,
            occurredAtEpochMillis: Long,
            errorCode: String,
        ) = Unit

        override fun observeResponse(response: ResponseHead, occurredAtEpochMillis: Long) = Unit

        override fun terminate(
            state: ExchangeState,
            timings: ExchangeTimings,
            occurredAtEpochMillis: Long,
            errorCode: String?,
        ) = Unit
    }

    private class DiscardInboundStreamHandler : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            ReferenceCountUtil.release(message)
        }
    }
}
