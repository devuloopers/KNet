package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.network.model.HttpTimings
import com.devuloopers.knet.domain.network.model.ProxyTrafficListener
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.AttributeKey
import java.net.InetAddress
import java.net.URI

private const val TAG = "ProxyEngine"

/**
 * Netty inbound handler that parses client requests, intercepts HTTPS CONNECT handshake,
 * and manages MITM decryption and request forwarding.
 */
@Suppress("HttpUrlsUsage")
class KNetProxyHandler(
    private val ca: CertificateAuthority,
    private val certCache: CertificateCache,
    private val listener: ProxyTrafficListener? = null
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    companion object {
        private val HOST_ATTR = AttributeKey.valueOf<String>("knet.host")
        private val PORT_ATTR = AttributeKey.valueOf<Int>("knet.port")
        private val SSL_ATTR = AttributeKey.valueOf<Boolean>("knet.ssl")
        private val TX_ID_ATTR = AttributeKey.valueOf<String>("knet.txId")
    }

    override fun channelRead0(context: ChannelHandlerContext, msg: FullHttpRequest) {
        if (msg.method().name() == "CONNECT") {
            handleConnect(context, msg)
        } else {
            handleRequest(context, msg)
        }
    }

    /**
     * Intercepts and handles HTTPS CONNECT requests from clients.
     * Establishes dynamic TLS context using CertificateCache and swaps HTTP codecs.
     */
    private fun handleConnect(context: ChannelHandlerContext, request: FullHttpRequest) {
        val uri = request.uri()
        val parts = uri.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toInt() else 443

        context.channel().attr(HOST_ATTR).set(host)
        context.channel().attr(PORT_ATTR).set(port)
        context.channel().attr(SSL_ATTR).set(true)

        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        context.writeAndFlush(response).addListener { future ->
            if (future.isSuccess) {
                val pipeline = context.pipeline()
                pipeline.remove("httpCodec")
                pipeline.remove("httpAggregator")

                val leaf = certCache.get(host, ca)
                val sslContext = SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate).build()

                pipeline.addFirst("ssl", sslContext.newHandler(context.alloc()))
                pipeline.addBefore("proxyHandler", "httpCodec", HttpServerCodec())
                pipeline.addBefore("proxyHandler", "httpAggregator", HttpObjectAggregator(10 * 1024 * 1024))
            } else {
                context.close()
            }
        }
    }

    /**
     * Relays plaintext HTTP or decrypted HTTPS client requests to the remote target server.
     */
    private fun handleRequest(context: ChannelHandlerContext, request: FullHttpRequest) {
        val channel = context.channel()
        val isSsl = channel.attr(SSL_ATTR).get() ?: false
        var targetHost = channel.attr(HOST_ATTR).get()
        var targetPort = channel.attr(PORT_ATTR).get() ?: 80

        if (targetHost == null) {
            val uri = request.uri()
            if (uri.startsWith("http://")) {
                val urlObj = URI.create(uri).toURL()
                targetHost = urlObj.host
                targetPort = if (urlObj.port != -1) urlObj.port else 80
            } else {
                val hostHeader = request.headers().get("Host")
                if (hostHeader != null) {
                    val parts = hostHeader.split(":")
                    targetHost = parts[0]
                    targetPort = if (parts.size > 1) parts[1].toInt() else 80
                }
            }
        }

        if (targetHost == null) {
            context.writeAndFlush(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST))
            return
        }

        val mappedRequest = HttpMapper.mapRequest(request, targetHost, isSsl)
        KNetLogger.info(TAG) { "KNet Proxy Captured: ${mappedRequest.method} ${mappedRequest.url}" }

        context.channel().attr(TX_ID_ATTR).set(mappedRequest.id)
        listener?.onRequestCaptured(mappedRequest)

        val relativeUri = if (request.uri().startsWith("http://") || request.uri().startsWith("https://")) {
            val urlObj = URI.create(request.uri()).toURL()
            val path = urlObj.path.ifEmpty { "/" }
            val query = urlObj.query
            if (query != null) "$path?$query" else path
        } else {
            request.uri()
        }

        val outboundRequest = DefaultFullHttpRequest(
            request.protocolVersion(),
            request.method(),
            relativeUri,
            request.content().retain()
        )
        outboundRequest.headers().set(request.headers())
        outboundRequest.headers().remove("Proxy-Connection")

        val dnsStartTime = System.currentTimeMillis()
        try {
            InetAddress.getByName(targetHost)
        } catch (_: Exception) {
            // Fall back if DNS lookup fails
        }
        val dnsDuration = (System.currentTimeMillis() - dnsStartTime).coerceAtLeast(0L)

        val connectStartTime = System.currentTimeMillis()
        var connectEndTime = connectStartTime
        val sslDurationHolder = longArrayOf(0L)

        val clientBootstrap = Bootstrap()
        clientBootstrap.group(context.channel().eventLoop())
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()

                    if (isSsl) {
                        val sslCtx = SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build()
                        val sslHandler = sslCtx.newHandler(ch.alloc(), targetHost, targetPort)
                        sslHandler.handshakeFuture().addListener { handshakeFuture ->
                            if (handshakeFuture.isSuccess) {
                                val sslEndTime = System.currentTimeMillis()
                                sslDurationHolder[0] = (sslEndTime - connectEndTime).coerceAtLeast(0L)
                            }
                        }
                        pipeline.addLast("ssl", sslHandler)
                    }
                    pipeline.addLast("httpCodec", HttpClientCodec())
                    pipeline.addLast("aggregator", HttpObjectAggregator(10 * 1024 * 1024))
                    pipeline.addLast(
                        "outboundHandler",
                        KNetOutboundHandler(
                            clientChannel = context.channel(),
                            request = outboundRequest,
                            listener = listener,
                            transactionId = mappedRequest.id,
                            getDnsDuration = { dnsDuration },
                            getTcpDuration = { (connectEndTime - connectStartTime).coerceAtLeast(0L) },
                            getSslDuration = { sslDurationHolder[0] }
                        )
                    )
                }
            })

        clientBootstrap.connect(targetHost, targetPort).addListener { future ->
            connectEndTime = System.currentTimeMillis()
            if (!future.isSuccess) {
                KNetLogger.error(TAG, future.cause()) { "KNet Proxy Failed to connect to $targetHost:$targetPort" }
                val errResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY)
                context.writeAndFlush(errResponse)
            }
        }
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        if (cause is java.io.IOException) {
            KNetLogger.debug(TAG) { "KNet Proxy IO Exception (normal connection close): ${cause.message}" }
        } else {
            KNetLogger.error(TAG, cause) { "KNet Proxy Exception: ${cause.message}" }
        }
        context.close()
    }
}

/**
 * Netty outbound client connection handler.
 * Fires the request to the remote server, listens to the response, maps it, and writes it back to the client.
 */
class KNetOutboundHandler(
    private val clientChannel: Channel,
    private val request: FullHttpRequest,
    private val listener: ProxyTrafficListener? = null,
    private val transactionId: String,
    private val getDnsDuration: () -> Long = { 0L },
    private val getTcpDuration: () -> Long = { 0L },
    private val getSslDuration: () -> Long = { 0L }
) : SimpleChannelInboundHandler<FullHttpResponse>() {

    private var requestSentTime: Long = System.currentTimeMillis()

    override fun channelActive(context: ChannelHandlerContext) {
        requestSentTime = System.currentTimeMillis()
        context.writeAndFlush(request)
    }

    override fun channelRead0(context: ChannelHandlerContext, msg: FullHttpResponse) {
        val responseReceivedTime = System.currentTimeMillis()
        val mappedResponse = HttpMapper.mapResponse(msg)
        KNetLogger.info(TAG) { "KNet Proxy Response: ${mappedResponse.statusCode} ${mappedResponse.statusText}" }

        val dnsDuration = getDnsDuration()
        val tcpDuration = getTcpDuration()
        val tlsDuration = getSslDuration()
        val ttfbDuration = (responseReceivedTime - requestSentTime).coerceAtLeast(0L)
        val downloadDuration = (System.currentTimeMillis() - responseReceivedTime).coerceAtLeast(0L)

        val totalDuration = dnsDuration + tcpDuration + tlsDuration + ttfbDuration + downloadDuration

        val timings = HttpTimings(
            dnsMs = dnsDuration,
            tcpMs = tcpDuration,
            tlsMs = tlsDuration,
            ttfbMs = ttfbDuration,
            downloadMs = downloadDuration
        )

        listener?.onResponseCaptured(
            transactionId = transactionId,
            response = mappedResponse,
            durationMs = totalDuration,
            timings = timings
        )

        msg.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)

        clientChannel.writeAndFlush(msg.retain()).addListener {
            context.close()
            clientChannel.close()
        }
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        if (cause is java.io.IOException) {
            KNetLogger.debug(TAG) { "KNet Outbound IO Exception (normal connection close): ${cause.message}" }
        } else {
            KNetLogger.error(TAG, cause) { "KNet Outbound Exception: ${cause.message}" }
        }
        context.close()
        clientChannel.close()
    }
}
