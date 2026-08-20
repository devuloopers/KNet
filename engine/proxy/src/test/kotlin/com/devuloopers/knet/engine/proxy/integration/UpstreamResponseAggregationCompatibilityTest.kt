package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyServer
import com.devuloopers.knet.engine.proxy.pipeline.SelectiveHttpObjectAggregator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpContent
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies full-message aggregation remains an explicit, opt-in breakpoint behavior. */
class UpstreamBreakpointAggregationTest {

    @Test
    fun `ordinary uploads stream while breakpoint consumers can request a full request`() {
        val streamingShape = observeRequestShape(requiresAggregation = false)
        assertFalse(streamingShape.sawFullRequest)
        assertTrue(streamingShape.sawContentChunk)

        val breakpointShape = observeRequestShape(requiresAggregation = true)
        assertTrue(breakpointShape.sawFullRequest)
    }

    @Test
    fun `ordinary responses stream while breakpoint consumers can request a full response`() {
        val streamingShape = observeResponseShape(requiresAggregation = false)
        assertFalse(streamingShape.sawFullResponse)
        assertTrue(streamingShape.sawContentChunk)

        val breakpointShape = observeResponseShape(requiresAggregation = true)
        assertTrue(breakpointShape.sawFullResponse)
    }

    /** Proxies one chunked response and records the objects written toward the client. */
    private fun observeResponseShape(requiresAggregation: Boolean): ResponseShape {
        val origin = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        val originThread = thread(name = "knet-aggregation-origin", isDaemon = true) {
            origin.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                socket.getOutputStream().write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n\r\n" +
                            "5\r\nhello\r\n" +
                            "0\r\n\r\n"
                    ).toByteArray(),
                )
                socket.getOutputStream().flush()
            }
        }

        val sawFullResponse = AtomicBoolean(false)
        val sawContentChunk = AtomicBoolean(false)
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
            pipelineInitializers = listOf({ pipeline ->
                pipeline.addLast(
                    "responseShapeObserver",
                    object : ChannelOutboundHandlerAdapter() {
                        override fun write(
                            context: ChannelHandlerContext,
                            message: Any,
                            promise: ChannelPromise,
                        ) {
                            if (message is FullHttpResponse) sawFullResponse.set(true)
                            if (message is HttpContent && message !is FullHttpResponse) {
                                sawContentChunk.set(true)
                            }
                            super.write(context, message, promise)
                        }
                    },
                )
            }),
            requiresFullResponseAggregation = { requiresAggregation },
        )
        proxy.start()

        try {
            Socket().use { client ->
                client.connect(proxy.boundAddress())
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().write(
                    (
                        "GET http://$authority/chunked HTTP/1.1\r\n" +
                            "Host: $authority\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray(),
                )
                client.getOutputStream().flush()
                val responseBytes = client.getInputStream().readBytes()
                assertTrue(responseBytes.toString(Charsets.US_ASCII).contains("hello"))
            }
            return ResponseShape(sawFullResponse.get(), sawContentChunk.get())
        } finally {
            proxy.stop()
            origin.close()
            originThread.join(1_000L)
        }
    }

    /** Proxies one upload and records the decoded objects seen by breakpoint handlers. */
    private fun observeRequestShape(requiresAggregation: Boolean): RequestShape {
        val origin = ServerSocket().apply {
            bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        }
        val originThread = thread(name = "knet-request-aggregation-origin", isDaemon = true) {
            origin.accept().use { socket ->
                val input = socket.getInputStream()
                val headerBytes = ArrayList<Byte>()
                var matchedTerminator = 0
                while (matchedTerminator < 4) {
                    val value = input.read()
                    require(value >= 0)
                    headerBytes += value.toByte()
                    matchedTerminator = when {
                        matchedTerminator == 0 && value == '\r'.code -> 1
                        matchedTerminator == 1 && value == '\n'.code -> 2
                        matchedTerminator == 2 && value == '\r'.code -> 3
                        matchedTerminator == 3 && value == '\n'.code -> 4
                        value == '\r'.code -> 1
                        else -> 0
                    }
                }
                repeat(5) { require(input.read() >= 0) }
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                )
                socket.getOutputStream().flush()
            }
        }

        val sawFullRequest = AtomicBoolean(false)
        val sawContentChunk = AtomicBoolean(false)
        val proxy = KNetProxyServer(
            port = availableLoopbackPort(),
            serverTlsContextProvider = com.devuloopers.knet.engine.proxy.TestServerTlsContextProvider(
                CertificateAuthority.generate(), CertificateCache(),
            ),
            pipelineInitializers = listOf({ pipeline ->
                pipeline.addLast(
                    "requestSelectiveAggregator",
                    SelectiveHttpObjectAggregator(1024) { _, _ -> requiresAggregation },
                )
                pipeline.addLast(
                    "requestShapeObserver",
                    object : ChannelInboundHandlerAdapter() {
                        override fun channelRead(context: ChannelHandlerContext, message: Any) {
                            if (message is FullHttpRequest) sawFullRequest.set(true)
                            if (message is HttpContent && message !is FullHttpRequest) {
                                sawContentChunk.set(true)
                            }
                            context.fireChannelRead(message)
                        }
                    },
                )
            }),
        )
        proxy.start()

        try {
            Socket().use { client ->
                client.connect(proxy.boundAddress())
                client.soTimeout = 5_000
                val authority = "${KNetProxyServer.DEFAULT_BIND_HOST}:${origin.localPort}"
                client.getOutputStream().write(
                    (
                        "POST http://$authority/upload HTTP/1.1\r\n" +
                            "Host: $authority\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello"
                    ).toByteArray(),
                )
                client.getOutputStream().flush()
                assertTrue(client.getInputStream().readBytes().toString(Charsets.US_ASCII).contains("200 OK"))
            }
            return RequestShape(sawFullRequest.get(), sawContentChunk.get())
        } finally {
            proxy.stop()
            origin.close()
            originThread.join(1_000L)
        }
    }

    /** Reserves an ephemeral loopback listener for the proxy. */
    private fun availableLoopbackPort(): Int = ServerSocket().use { socket ->
        socket.bind(InetSocketAddress(KNetProxyServer.DEFAULT_BIND_HOST, 0))
        socket.localPort
    }

    /** Outbound Netty message shape observed at the downstream breakpoint boundary. */
    private data class ResponseShape(
        val sawFullResponse: Boolean,
        val sawContentChunk: Boolean,
    )

    /** Inbound Netty message shape observed at the downstream breakpoint boundary. */
    private data class RequestShape(
        val sawFullRequest: Boolean,
        val sawContentChunk: Boolean,
    )
}
