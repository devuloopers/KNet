package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.clientNetwork.model.ProxyTrafficListener
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.dns.NettyDnsResolver
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.ssl.ProxyTrustManager
import com.devuloopers.knet.engine.proxy.timing.NetworkTimingCollector
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val listener: ProxyTrafficListener? = null,
    private val keyManagerProvider: com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider? = null,
    private val strictSsl: Boolean = false
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    companion object {
        private val HOST_ATTR = AttributeKey.valueOf<String>("knet.host")
        private val PORT_ATTR = AttributeKey.valueOf<Int>("knet.port")
        private val SSL_ATTR = AttributeKey.valueOf<Boolean>("knet.ssl")
        private val TX_ID_ATTR = AttributeKey.valueOf<String>("knet.txId")
    }

    override fun channelActive(context: ChannelHandlerContext) {
        super.channelActive(context)
    }

    override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
        if (request.method() == HttpMethod.CONNECT) {
            handleConnect(context, request)
        } else {
            handleRequest(context, request)
        }
    }

    private fun handleConnect(context: ChannelHandlerContext, request: FullHttpRequest) {
        val uri = request.uri()
        val parts = uri.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toInt() else 443

        context.channel().attr(HOST_ATTR).set(host)
        context.channel().attr(PORT_ATTR).set(port)
        context.channel().attr(SSL_ATTR).set(true)

        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus(200, "Connection Established")
        )
        response.headers().set("Proxy-Agent", "KNet")
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

    private fun handleRequest(context: ChannelHandlerContext, request: FullHttpRequest) {
        val channel = context.channel()
        val isSsl = channel.attr(SSL_ATTR).get() ?: false
        var targetHost = channel.attr(HOST_ATTR).get()
        var targetPort = channel.attr(PORT_ATTR).get() ?: 80

        if (targetHost == null) {
            val uri = request.uri()
            if (uri.startsWith("http://")) {
                val urlObj = URI.create(uri)
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
            KNetLogger.error(TAG) { "Failed to extract target host for request ${request.uri()}" }
            val errResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
            context.writeAndFlush(errResponse).addListener { context.close() }
            return
        }

        val localBoundPort = (context.channel().localAddress() as? java.net.InetSocketAddress)?.port ?: -1
        if (isSelfTarget(targetHost, targetPort, localBoundPort)) {
            KNetLogger.warn(TAG) { "[SELF PROXY GUARD] Blocked recursive self-proxy connection to $targetHost:$targetPort" }
            val errBody = "400 Bad Request: Recursive Self-Proxy Connection Blocked".toByteArray(Charsets.UTF_8)
            val errResponse = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.BAD_REQUEST,
                Unpooled.copiedBuffer(errBody)
            )
            errResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
            errResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errBody.size)
            context.writeAndFlush(errResponse).addListener { context.close() }
            return
        }

        val relativeUri = if (request.uri().startsWith("http://")) {
            val uriObj = URI.create(request.uri())
            val rawPath = uriObj.rawPath.ifEmpty { "/" }
            if (uriObj.rawQuery != null) "$rawPath?${uriObj.rawQuery}" else rawPath
        } else {
            request.uri()
        }

        val mappedRequest = HttpMapper.mapRequest(request, isSsl, targetHost, targetPort, relativeUri)
        KNetLogger.info(TAG) { "KNet Proxy Intercepted: ${mappedRequest.method} ${mappedRequest.url}" }
        listener?.onRequestCaptured(mappedRequest)

        val outboundRequest = DefaultFullHttpRequest(
            request.protocolVersion(),
            request.method(),
            relativeUri,
            request.content().retain()
        )
        outboundRequest.headers().set(request.headers())
        outboundRequest.headers().remove("Proxy-Connection")
        outboundRequest.headers().set(
            HttpHeaderNames.HOST,
            if (targetPort == 80 || targetPort == 443) targetHost else "$targetHost:$targetPort"
        )

        val timingCollector = NetworkTimingCollector()
        timingCollector.markDnsStart()

        CoroutineScope(Dispatchers.IO).launch {
            val resolvedHost = try {
                val addr = InetAddress.getByName(targetHost)
                timingCollector.markDnsEnd()
                addr.hostAddress
            } catch (_: Exception) {
                timingCollector.markDnsEnd()
                targetHost
            }

            context.channel().eventLoop().execute {
                timingCollector.markTcpStart()

                val clientBootstrap = Bootstrap()
                clientBootstrap.group(context.channel().eventLoop())
                    .channel(NioSocketChannel::class.java)
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, com.devuloopers.knet.domain.clientNetwork.model.NetworkTimeouts.DEFAULT_TIMEOUT_INT_MS)
                    .resolver(NettyDnsResolver.resolverGroup)
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val pipeline = ch.pipeline()

                            if (isSsl) {
                                timingCollector.markTlsStart()
                                val sslCtxBuilder = SslContextBuilder.forClient()
                                    .trustManager(ProxyTrustManager.getTrustManagerFactory(strictSsl))

                                val kmf = keyManagerProvider?.getKeyManagerFactory(targetHost)
                                if (kmf != null) {
                                    sslCtxBuilder.keyManager(kmf)
                                }

                                val sslCtx = sslCtxBuilder.build()
                                val sslHandler = sslCtx.newHandler(ch.alloc(), targetHost, targetPort)
                                 sslHandler.handshakeFuture().addListener { handshakeFuture ->
                                    if (handshakeFuture.isSuccess) {
                                        timingCollector.markTlsEnd()
                                    } else {
                                        val causeMsg = handshakeFuture.cause()?.message ?: "SSL/TLS Handshake failed for $targetHost"
                                        KNetLogger.error(TAG, handshakeFuture.cause()) { "SSL Handshake Failed for $targetHost: $causeMsg" }

                                        val errBodyBytes = "502 Bad Gateway: SSL Handshake Failed ($causeMsg)".toByteArray(Charsets.UTF_8)
                                        val errResponse = DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.BAD_GATEWAY,
                                            Unpooled.copiedBuffer(errBodyBytes)
                                        )
                                        errResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                                        errResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errBodyBytes.size)

                                        val mappedErr = HttpMapper.mapResponse(errResponse)
                                        listener?.onResponseCaptured(
                                            transactionId = mappedRequest.id,
                                            response = mappedErr,
                                            durationMs = timingCollector.getTotalDuration(),
                                            timings = timingCollector.getTimings()
                                        )

                                        context.writeAndFlush(errResponse).addListener { context.close() }
                                    }
                                }
                                pipeline.addLast("ssl", sslHandler)
                            }
                            pipeline.addLast("httpCodec", HttpClientCodec())
                            pipeline.addLast(
                                "outboundHandler",
                                KNetOutboundHandler(
                                    clientChannel = context.channel(),
                                    request = outboundRequest,
                                    listener = listener,
                                    transactionId = mappedRequest.id,
                                    timingCollector = timingCollector
                                )
                            )
                        }
                    })

                clientBootstrap.connect(resolvedHost, targetPort).addListener { future ->
                    timingCollector.markTcpEnd()
                    if (!future.isSuccess) {
                        val causeMessage = future.cause()?.message ?: "Could not resolve or establish connection to $targetHost:$targetPort"
                        KNetLogger.error(TAG, future.cause()) { "KNet Proxy Failed to connect to $targetHost:$targetPort - $causeMessage" }

                        val errBodyBytes = "502 Bad Gateway: $causeMessage".toByteArray(Charsets.UTF_8)
                        val errResponse = DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.BAD_GATEWAY,
                            Unpooled.copiedBuffer(errBodyBytes)
                        )
                        errResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                        errResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errBodyBytes.size)

                        val mappedErr = HttpMapper.mapResponse(errResponse)
                        listener?.onResponseCaptured(
                            transactionId = mappedRequest.id,
                            response = mappedErr,
                            durationMs = timingCollector.getTotalDuration(),
                            timings = timingCollector.getTimings()
                        )

                        context.writeAndFlush(errResponse).addListener { context.close() }
                    }
                }
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

    /**
     * Determines whether a target host and port represent a recursive self-proxy loop to KNet itself.
     */
    private fun isSelfTarget(targetHost: String, targetPort: Int, localBoundPort: Int): Boolean {
        val isLocalHost = targetHost == "127.0.0.1" ||
                targetHost == "localhost" ||
                targetHost.equals("knet.local", ignoreCase = true) ||
                isLocalMachineIp(targetHost)

        return isLocalHost && (targetPort == localBoundPort || targetPort == 8080)
    }

    /**
     * Checks if the given host string corresponds to a local machine network interface IP.
     */
    private fun isLocalMachineIp(host: String): Boolean {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    if (addrs.nextElement().hostAddress == host) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Netty outbound client connection handler supporting real-time chunked response streaming.
 * Streams HttpResponse and HttpContent to client channel while accumulating body bytes
 * for proxy inspection. Chunked responses arrive as separate HttpContent messages;
 * this handler buffers them in a [java.io.ByteArrayOutputStream] and merges the
 * accumulated body into the mapped [com.devuloopers.knet.domain.clientNetwork.model.HttpResponse]
 * on [io.netty.handler.codec.http.LastHttpContent] before firing [ProxyTrafficListener.onResponseCaptured].
 */
class KNetOutboundHandler(
    private val clientChannel: Channel,
    private val request: FullHttpRequest,
    private val listener: ProxyTrafficListener? = null,
    private val transactionId: String,
    private val timingCollector: NetworkTimingCollector = NetworkTimingCollector()
) : SimpleChannelInboundHandler<HttpObject>() {

    private var mappedResponse: com.devuloopers.knet.domain.clientNetwork.model.HttpResponse? = null
    private var isKeepAlive: Boolean = true

    /** Accumulates body bytes from chunked HttpContent messages for inspection capture. */
    private val responseBodyBuffer = java.io.ByteArrayOutputStream()

    override fun channelActive(context: ChannelHandlerContext) {
        timingCollector.markRequestSent()
        context.writeAndFlush(request).addListener {
            timingCollector.markRequestSent()
        }
    }

    override fun channelRead0(context: ChannelHandlerContext, msg: HttpObject) {
        when (msg) {
            is HttpResponse -> {
                timingCollector.markFirstByteReceived()

                if (msg is FullHttpResponse) {
                    mappedResponse = HttpMapper.mapResponse(msg)
                } else {
                    mappedResponse = HttpMapper.mapResponseHeaders(msg)
                }
                isKeepAlive = HttpUtil.isKeepAlive(msg)
                KNetLogger.info(TAG) { "KNet Proxy Response Headers: ${msg.status()}" }

                if (!isKeepAlive) {
                    msg.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                }
                clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
            }

            is HttpContent -> {
                // Accumulate body chunk bytes for inspection capture before forwarding
                val chunk = msg.content()
                if (chunk.readableBytes() > 0) {
                    val bytes = ByteArray(chunk.readableBytes())
                    chunk.getBytes(chunk.readerIndex(), bytes)
                    responseBodyBuffer.write(bytes)
                }

                clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))

                if (msg is LastHttpContent) {
                    timingCollector.markLastByteReceived()

                    val timings = timingCollector.getTimings()
                    val totalDuration = timingCollector.getTotalDuration()

                    // Merge accumulated chunked body bytes into the mapped response.
                    // For FullHttpResponse, body is already extracted by mapResponse();
                    // for chunked responses, mapResponseHeaders() left body = null.
                    val accumulatedBody = responseBodyBuffer.toByteArray().takeIf { it.isNotEmpty() }
                    val finalResponse = mappedResponse?.let { response ->
                        com.devuloopers.knet.domain.clientNetwork.model.HttpResponse(
                            statusCode = response.statusCode,
                            statusText = response.statusText,
                            headers = response.headers,
                            body = response.body ?: accumulatedBody,
                            timestamp = response.timestamp
                        )
                    }
                    responseBodyBuffer.reset()

                    finalResponse?.let { response ->
                        listener?.onResponseCaptured(
                            transactionId = transactionId,
                            response = response,
                            durationMs = totalDuration,
                            timings = timings
                        )
                    }

                    // Always close the one-shot outbound remote server channel to release socket file descriptor
                    context.close()

                    if (!isKeepAlive) {
                        clientChannel.close()
                    }
                }
            }
        }
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        if (cause is java.io.IOException) {
            KNetLogger.debug(TAG) { "KNet Outbound IO Exception (normal connection close): ${cause.message}" }
        } else {
            KNetLogger.error(TAG, cause) { "KNet Outbound Exception: ${cause.message}" }
        }

        if (mappedResponse == null) {
            val causeMessage = cause.message ?: "Outbound Proxy Exception: ${cause::class.simpleName}"
            val errBodyBytes = "502 Bad Gateway: $causeMessage".toByteArray(Charsets.UTF_8)
            val errResponse = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.BAD_GATEWAY,
                Unpooled.copiedBuffer(errBodyBytes)
            )
            errResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
            errResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errBodyBytes.size)

            val mappedErr = HttpMapper.mapResponse(errResponse)
            listener?.onResponseCaptured(
                transactionId = transactionId,
                response = mappedErr,
                durationMs = timingCollector.getTotalDuration(),
                timings = timingCollector.getTimings()
            )
        }

        context.close()
        clientChannel.close()
    }
}
