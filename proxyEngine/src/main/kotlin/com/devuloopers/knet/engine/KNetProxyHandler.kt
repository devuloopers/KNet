package com.devuloopers.knet.engine

import com.devuloopers.knet.crypto.CertificateAuthority
import com.devuloopers.knet.crypto.CertificateCache
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
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.util.AttributeKey
import com.devuloopers.knet.engine.util.HttpMapper
import com.devuloopers.knet.logger.KNetLogger
import com.devuloopers.knet.model.ProxyTrafficListener
import java.net.URI
import java.net.URL

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
     * Establishes dynamic TLS context using LeafCertificateGenerator and swaps HTTP codecs.
     */
    private fun handleConnect(context: ChannelHandlerContext, request: FullHttpRequest) {
        val uri = request.uri()
        val parts = uri.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toInt() else 443

        // Cache the hostname and SSL metadata on the channel context for downstream requests.
        context.channel().attr(HOST_ATTR).set(host)
        context.channel().attr(PORT_ATTR).set(port)
        context.channel().attr(SSL_ATTR).set(true)

        // Write HTTP 200 Connection Established to let the client start TLS.
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        context.writeAndFlush(response).addListener { future ->
            if (future.isSuccess) {
                // Remove plaintext HTTP server codecs.
                val pipeline = context.pipeline()
                pipeline.remove("httpCodec")
                pipeline.remove("httpAggregator")

                // Generate dynamic leaf certificate matching the requested host.
                val leaf = certCache.get(host, ca)

                // Instantiate Netty SSL context using generated certificate keys.
                val sslContext = SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate).build()

                // Insert client-side SslHandler at the front of the pipeline.
                pipeline.addFirst("ssl", sslContext.newHandler(context.alloc()))

                // Re-add HTTP server codecs before proxyHandler to parse decrypted streams.
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
            // Extract from absolute URI if this is an unencrypted HTTP proxy request.
            val uri = request.uri()
            if (uri.startsWith("http://")) {
                val urlObj = URI.create(uri).toURL()
                targetHost = urlObj.host
                targetPort = if (urlObj.port != -1) urlObj.port else 80
            } else {
                // Fallback to Host header
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

        // Map client netty request to common DTO model
        val mappedRequest = HttpMapper.mapRequest(request, targetHost, isSsl)
        
        // Log the captured request URL
        KNetLogger.info(TAG) { "KNet Proxy Captured: ${mappedRequest.method} ${mappedRequest.url}" }

        // Store transaction ID on channel context attributes
        context.channel().attr(TX_ID_ATTR).set(mappedRequest.id)

        // Dispatch capture notification to listener
        listener?.onRequestCaptured(mappedRequest)

        // Transform absolute URI proxy request to relative URI for outbound request.
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
            java.net.InetAddress.getByName(targetHost)
        } catch (_: Exception) {
            // Fall back to unresolved hostname if lookup fails
        }
        val dnsDuration = (System.currentTimeMillis() - dnsStartTime).coerceAtLeast(0L)

        val connectStartTime = System.currentTimeMillis()
        var connectEndTime = connectStartTime
        var sslEndTime = connectStartTime
        val sslDurationHolder = longArrayOf(0L)

        // Establish connection to target remote server.
        val clientBootstrap = Bootstrap()
        clientBootstrap.group(context.channel().eventLoop()) // Reuse client event loop thread
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()

                    if (isSsl) {
                        // Trust all certificates during outbound proxy forwarding (relying on client verification)
                        val sslCtx = SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build()
                        val sslHandler = sslCtx.newHandler(ch.alloc(), targetHost, targetPort)
                        sslHandler.handshakeFuture().addListener { handshakeFuture ->
                            if (handshakeFuture.isSuccess) {
                                sslEndTime = System.currentTimeMillis()
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
                            connectStartTime = connectStartTime,
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
    private val connectStartTime: Long = System.currentTimeMillis(),
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

        val timings = com.devuloopers.knet.model.HttpTimings(
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

        // Set connection close header to tell the client we are closing the socket
        msg.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)

        // Write the response back to client and close outbound channel.
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
