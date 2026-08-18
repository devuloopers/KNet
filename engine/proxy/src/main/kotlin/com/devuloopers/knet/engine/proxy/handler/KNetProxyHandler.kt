package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.CertificateCache
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.ProxyConnectionAdmissionController
import com.devuloopers.knet.engine.proxy.dns.NettyDnsResolver
import com.devuloopers.knet.engine.proxy.capture.ProxyConnectionCapture
import com.devuloopers.knet.engine.proxy.capture.ProxyExchangeCapture
import com.devuloopers.knet.engine.proxy.http.AuthorityParseResult
import com.devuloopers.knet.engine.proxy.http.AuthorityParser
import com.devuloopers.knet.engine.proxy.mapper.HttpMapper
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.ssl.ProxyTrustManager
import com.devuloopers.knet.engine.proxy.timing.NetworkTimingCollector
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest

import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequest as NettyHttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.ExchangeState
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.body.ContentEncoding

private const val TAG = "ProxyEngine"

/**
 * Netty inbound handler that parses client requests, intercepts HTTPS CONNECT handshake,
 * and manages MITM decryption and request forwarding.
 */
@Suppress("HttpUrlsUsage")
class KNetProxyHandler(
    private val ca: CertificateAuthority,
    private val certCache: CertificateCache,
    private val keyManagerProvider: com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider? = null,
    private val strictSsl: Boolean = true,
    private val proxyScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val runtimePolicy: KNetProxyRuntimePolicy = KNetProxyRuntimePolicy(),
    private val admissionController: ProxyConnectionAdmissionController =
        ProxyConnectionAdmissionController(runtimePolicy),
    private val certificateExecutor: Executor = ForkJoinPool.commonPool(),
    private val connectionCapture: ProxyConnectionCapture? = null,
    private val requiresFullResponseAggregation: () -> Boolean = { false },
) : SimpleChannelInboundHandler<FullHttpRequest>() {

    private val pendingRequests = ArrayDeque<FullHttpRequest>()
    private var requestInFlight: Boolean = false

    companion object {
        private const val MAX_PIPELINED_REQUESTS: Int = 16
        private val HOST_ATTR = AttributeKey.valueOf<String>("knet.host")
        private val PORT_ATTR = AttributeKey.valueOf<Int>("knet.port")
        private val SSL_ATTR = AttributeKey.valueOf<Boolean>("knet.ssl")
        private val TX_ID_ATTR = AttributeKey.valueOf<String>("knet.txId")
        private const val UPSTREAM_LIMIT_ERROR: String = "upstream_connection_limit"
        private const val TLS_HANDSHAKE_ERROR: String = "upstream_tls_handshake_failed"
        private const val UPSTREAM_CONNECT_ERROR: String = "upstream_connect_failed"
    }

    override fun channelActive(context: ChannelHandlerContext) {
        super.channelActive(context)
    }

    override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
        if (request.method() == HttpMethod.CONNECT) {
            handleConnect(context, request)
        } else if (requestInFlight) {
            if (pendingRequests.size >= MAX_PIPELINED_REQUESTS) {
                KNetLogger.warn(TAG) { "Closing client that exceeded the bounded HTTP/1 request queue." }
                val response = DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.TOO_MANY_REQUESTS,
                )
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
                context.writeAndFlush(response).addListener { context.close() }
            } else {
                // SimpleChannelInboundHandler releases the callback reference; the queue owns this retained reference.
                pendingRequests.addLast(request.retain())
            }
        } else {
            requestInFlight = true
            handleRequest(context, request)
        }
    }

    private fun handleConnect(context: ChannelHandlerContext, request: FullHttpRequest) {
        val parsedAuthority = AuthorityParser.parse(request.uri(), defaultPort = 443)
        if (parsedAuthority !is AuthorityParseResult.Valid) {
            writeBadRequest(context, "Invalid CONNECT authority")
            return
        }
        val host = parsedAuthority.authority.host
        val port = parsedAuthority.authority.port

        context.channel().attr(HOST_ATTR).set(host)
        context.channel().attr(PORT_ATTR).set(port)
        context.channel().attr(SSL_ATTR).set(true)

        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus(200, "Connection Established")
        )
        response.headers().set("Proxy-Agent", "KNet")
        // Do not allow pipelined TLS records to reach the HTTP decoder while certificate material
        // is generated on the bounded crypto worker.
        context.channel().config().isAutoRead = false
        context.writeAndFlush(response).addListener { future ->
            if (future.isSuccess) {
                certCache.getAsync(host, ca, certificateExecutor)
                    .thenApplyAsync({ leaf ->
                        SslContextBuilder.forServer(leaf.keyPair.private, leaf.certificate).build()
                    }, certificateExecutor)
                    .whenComplete { sslContext, failure ->
                        context.executor().execute {
                            if (failure != null || sslContext == null || !context.channel().isActive) {
                                if (failure != null) {
                                    KNetLogger.error(TAG, failure) {
                                        "Failed to configure SSL pipeline for $host: ${failure.message}"
                                    }
                                }
                                context.close()
                                return@execute
                            }
                            try {
                                val pipeline = context.pipeline()
                                pipeline.remove(PipelineHandlerNames.HTTP_CODEC)
                                pipeline.remove(PipelineHandlerNames.HTTP_AGGREGATOR)
                                val sslHandler = sslContext.newHandler(context.alloc()).apply {
                                    setHandshakeTimeoutMillis(runtimePolicy.tlsHandshakeTimeoutMillis)
                                }
                                pipeline.addFirst(PipelineHandlerNames.SSL, sslHandler)
                                pipeline.addAfter(PipelineHandlerNames.SSL, PipelineHandlerNames.HTTP_CODEC, HttpServerCodec())
                                pipeline.addAfter(
                                    PipelineHandlerNames.HTTP_CODEC,
                                    PipelineHandlerNames.HTTP_AGGREGATOR,
                                    HttpObjectAggregator(PipelineHandlerNames.MAX_CONTENT_LENGTH_BYTES)
                                )
                                context.channel().config().isAutoRead = true
                            } catch (pipelineFailure: Exception) {
                                KNetLogger.error(TAG, pipelineFailure) {
                                    "Failed to configure SSL pipeline for $host: ${pipelineFailure.message}"
                                }
                                context.close()
                            }
                        }
                    }
            } else {
                context.close()
            }
        }
    }

    private fun handleRequest(context: ChannelHandlerContext, request: FullHttpRequest) {
        val exchangeCompleted = AtomicBoolean(false)
        val finishExchange = {
            if (exchangeCompleted.compareAndSet(false, true)) {
                completeCurrentExchange(context)
            }
        }
        val channel = context.channel()
        val isSsl = channel.attr(SSL_ATTR).get() ?: false
        var targetHost = channel.attr(HOST_ATTR).get()
        var targetPort = channel.attr(PORT_ATTR).get() ?: 80

        if (targetHost == null) {
            val uri = request.uri()
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                val urlObject = runCatching { URI.create(uri) }.getOrNull()
                targetHost = urlObject?.host
                targetPort = when {
                    urlObject?.port != null && urlObject.port != -1 -> urlObject.port
                    urlObject?.scheme.equals("https", ignoreCase = true) -> 443
                    else -> 80
                }
            } else {
                val hostHeader = request.headers().get("Host")
                if (hostHeader != null) {
                    when (val authority = AuthorityParser.parse(hostHeader, defaultPort = 80)) {
                        is AuthorityParseResult.Valid -> {
                            targetHost = authority.authority.host
                            targetPort = authority.authority.port
                        }
                        is AuthorityParseResult.Invalid -> {
                            writeBadRequest(context, "Invalid Host authority")
                            return
                        }
                    }
                }
            }
        }

        if (targetHost == null) {
            KNetLogger.error(TAG) { "Failed to extract target host for request ${request.uri()}" }
            writeBadRequest(context, "Missing target authority")
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

        val relativeUri = if (request.uri().startsWith("http://") || request.uri().startsWith("https://")) {
            val uriObject = runCatching { URI.create(request.uri()) }.getOrNull()
            if (uriObject == null) {
                writeBadRequest(context, "Invalid absolute request target")
                return
            }
            val rawPath = uriObject.rawPath.ifEmpty { "/" }
            if (uriObject.rawQuery != null) "$rawPath?${uriObject.rawQuery}" else rawPath
        } else {
            request.uri()
        }

        val taggedReq = context.channel().attr(ProxyChannelAttributes.REQUEST_CONTEXT).get()
        // The interceptor maps every aggregated request freshly and may consume KNet's internal
        // correlation header. Reuse that request regardless of breakpoint state so the stable ID
        // cannot be replaced between adjacent pipeline handlers.
        val mappedRequest = taggedReq
            ?: HttpMapper.mapRequestContext(request, isSsl, targetHost, targetPort, relativeUri)
        val exchangeCapture = connectionCapture?.startExchange(
            exchangeId = mappedRequest.exchangeId,
            request = mappedRequest.request.head,
            occurredAtEpochMillis = mappedRequest.startedAtEpochMillis,
        )
        captureBodyChunk(
            exchange = exchangeCapture,
            direction = TrafficDirection.CLIENT_TO_SERVER,
            content = request.content(),
            contentEncoding = HttpMapper.contentEncoding(request.headers()),
        )
        exchangeCapture?.completeBody(
            direction = TrafficDirection.CLIENT_TO_SERVER,
            observedBytes = request.content().readableBytes().toLong(),
            occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        KNetLogger.info(TAG) {
            "Captured request: ${mappedRequest.request.head.method.token} ${mappedRequest.request.absoluteUrl()}"
        }

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

        proxyScope.launch {
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

                val upstreamLease = admissionController.tryAcquireUpstream()
                if (upstreamLease == null) {
                    val errResponse = DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.SERVICE_UNAVAILABLE,
                    )
                    errResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
                    publishFailedResponse(exchangeCapture, errResponse, timingCollector, UPSTREAM_LIMIT_ERROR)
                    ReferenceCountUtil.release(outboundRequest)
                    context.writeAndFlush(errResponse).addListener {
                        finishExchange()
                    }
                    return@execute
                }

                val clientBootstrap = Bootstrap()
                clientBootstrap.group(context.channel().eventLoop())
                    .channel(NioSocketChannel::class.java)
                    // Response reads are advanced explicitly only after the previous decoded batch
                    // has been flushed to the downstream channel. This bounds queued transport data
                    // when a client is slower than its origin.
                    .option(ChannelOption.AUTO_READ, false)
                    .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        runtimePolicy.connectTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    )
                    .resolver(NettyDnsResolver.resolverGroup)
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val pipeline = ch.pipeline()

                            pipeline.addLast(
                                PipelineHandlerNames.READ_TIMEOUT,
                                ReadTimeoutHandler(runtimePolicy.readIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                            )
                            pipeline.addLast(
                                PipelineHandlerNames.WRITE_TIMEOUT,
                                WriteTimeoutHandler(runtimePolicy.writeIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                            )

                            if (isSsl) {
                                timingCollector.markTlsStart()
                                val sslCtxBuilder = SslContextBuilder.forClient()
                                    .trustManager(ProxyTrustManager.getTrustManagerFactory(strictSsl))

                                val kmf = keyManagerProvider?.getKeyManagerFactory(targetHost)
                                if (kmf != null) {
                                    sslCtxBuilder.keyManager(kmf)
                                }

                                val sslCtx = sslCtxBuilder.build()
                                val sslHandler = sslCtx.newHandler(ch.alloc(), targetHost, targetPort).apply {
                                    setHandshakeTimeoutMillis(runtimePolicy.tlsHandshakeTimeoutMillis)
                                }
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

                                        publishFailedResponse(exchangeCapture, errResponse, timingCollector, TLS_HANDSHAKE_ERROR)

                                        context.writeAndFlush(errResponse).addListener {
                                            finishExchange()
                                            context.close()
                                            ch.close()
                                        }
                                    }
                                }
                                pipeline.addLast(PipelineHandlerNames.SSL, sslHandler)
                            }
                            pipeline.addLast(PipelineHandlerNames.HTTP_CODEC, HttpClientCodec())
                            if (requiresFullResponseAggregation()) {
                                pipeline.addLast(
                                    PipelineHandlerNames.HTTP_AGGREGATOR,
                                    HttpObjectAggregator(PipelineHandlerNames.MAX_CONTENT_LENGTH_BYTES),
                                )
                            }
                            pipeline.addLast(
                                PipelineHandlerNames.OUTBOUND_HANDLER,
                                KNetOutboundHandler(
                                    clientChannel = context.channel(),
                                    request = outboundRequest,
                                    timingCollector = timingCollector,
                                    onExchangeComplete = finishExchange,
                                    capture = exchangeCapture,
                                )
                            )
                        }
                    })

                val connectFuture = clientBootstrap.connect(resolvedHost, targetPort)
                connectFuture.addListener { future ->
                    timingCollector.markTcpEnd()
                    if (future.isSuccess) {
                        connectFuture.channel().closeFuture().addListener { upstreamLease.close() }
                    } else {
                        upstreamLease.close()
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

                        publishFailedResponse(exchangeCapture, errResponse, timingCollector, UPSTREAM_CONNECT_ERROR)

                        ReferenceCountUtil.release(outboundRequest)
                        context.writeAndFlush(errResponse).addListener {
                            finishExchange()
                            context.close()
                        }
                    }
                }
            }
        }
    }

    /** Publishes a generated terminal response through canonical capture without blocking forwarding. */
    private fun publishFailedResponse(
        capture: ProxyExchangeCapture?,
        response: HttpResponse,
        timingCollector: NetworkTimingCollector,
        errorCode: String,
    ) {
        if (capture == null) return
        val now = Clock.System.now().toEpochMilliseconds()
        capture.observeResponse(HttpMapper.mapResponseHead(response), now)
        capture.terminate(
            state = ExchangeState.FAILED,
            timings = timingCollector.getTimings(),
            occurredAtEpochMillis = now,
            errorCode = errorCode,
        )
    }

    /**
     * Advances the bounded per-connection HTTP/1 queue only after the current response write completes.
     * This temporarily serializes pipelined exchanges until the streaming transport state machine lands.
     */
    private fun completeCurrentExchange(context: ChannelHandlerContext) {
        if (!context.executor().inEventLoop()) {
            context.executor().execute { completeCurrentExchange(context) }
            return
        }
        if (!context.channel().isActive) {
            requestInFlight = false
            releasePendingRequests()
            return
        }

        val nextRequest = pendingRequests.removeFirstOrNull()
        if (nextRequest == null) {
            requestInFlight = false
            return
        }

        try {
            handleRequest(context, nextRequest)
        } finally {
            ReferenceCountUtil.release(nextRequest)
        }
    }

    /** Releases every queued request when the downstream connection terminates. */
    private fun releasePendingRequests() {
        while (pendingRequests.isNotEmpty()) {
            ReferenceCountUtil.release(pendingRequests.removeFirst())
        }
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        requestInFlight = false
        releasePendingRequests()
        super.channelInactive(context)
    }

    /** Writes a bounded client error and closes the invalid downstream connection. */
    private fun writeBadRequest(context: ChannelHandlerContext, reason: String) {
        KNetLogger.warn(TAG) { "Rejected proxy request: $reason" }
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0)
        context.writeAndFlush(response).addListener { context.close() }
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
 * Streams upstream response heads/content to the downstream channel under explicit read-after-write
 * flow control. Production capture receives reservation-bounded chunks through [ProxyExchangeCapture].
 */
class KNetOutboundHandler(
    private val clientChannel: Channel,
    private val request: NettyHttpRequest,
    private val timingCollector: NetworkTimingCollector = NetworkTimingCollector(),
    private val onExchangeComplete: () -> Unit = {},
    private val capture: ProxyExchangeCapture? = null,
    private val onRequestHeadWritten: (Channel) -> Unit = {},
    private val onUpstreamWritable: () -> Unit = {},
) : SimpleChannelInboundHandler<HttpObject>() {

    companion object {
        private const val OUTBOUND_FAILURE: String = "upstream_response_failed"
    }

    private var responseStarted: Boolean = false
    private var isKeepAlive: Boolean = true
    private val completionPublished = AtomicBoolean(false)
    private var lastClientWrite: io.netty.channel.ChannelFuture? = null
    private var responseComplete: Boolean = false
    private var provisionalResponseInProgress: Boolean = false

    private var responseObservedBytes: Long = 0L
    private var responseContentEncoding: ContentEncoding? = null

    override fun channelActive(context: ChannelHandlerContext) {
        context.writeAndFlush(request).addListener { writeFuture ->
            if (writeFuture.isSuccess) {
                timingCollector.markRequestSent()
                onRequestHeadWritten(context.channel())
                context.read()
            } else {
                context.fireExceptionCaught(
                    writeFuture.cause() ?: java.io.IOException("Failed to write request to upstream."),
                )
            }
        }
    }

    override fun channelWritabilityChanged(context: ChannelHandlerContext) {
        if (context.channel().isWritable) onUpstreamWritable()
        super.channelWritabilityChanged(context)
    }

    override fun channelRead0(context: ChannelHandlerContext, msg: HttpObject) {
        when (msg) {
            is FullHttpResponse -> {
                if (msg.status().code() in 100..199 && msg.status().code() != 101) {
                    KNetLogger.debug(TAG) { "KNet Proxy Provisional Full Response: ${msg.status()}" }
                    lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                    return
                }
                timingCollector.markFirstByteReceived()
                timingCollector.markLastByteReceived()

                val timings = timingCollector.getTimings()

                responseStarted = true
                isKeepAlive = HttpUtil.isKeepAlive(msg)
                KNetLogger.info(TAG) { "KNet Proxy Full Response: ${msg.status()}" }

                val now = Clock.System.now().toEpochMilliseconds()
                capture?.observeResponse(HttpMapper.mapResponseHead(msg), now)
                captureBodyChunk(
                    exchange = capture,
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    content = msg.content(),
                    contentEncoding = HttpMapper.contentEncoding(msg.headers()),
                )
                capture?.completeBody(
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    observedBytes = msg.content().readableBytes().toLong(),
                    occurredAtEpochMillis = now,
                )
                capture?.terminate(
                    state = ExchangeState.COMPLETED,
                    timings = timings,
                    occurredAtEpochMillis = now,
                )

                if (!isKeepAlive) {
                    msg.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                }

                responseComplete = true
                lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg)).addListener {
                    publishCompletion()
                    if (!isKeepAlive) {
                        clientChannel.close()
                    }
                    context.close()
                }
            }

            is HttpResponse -> {
                provisionalResponseInProgress =
                    msg.status().code() in 100..199 && msg.status().code() != 101
                if (provisionalResponseInProgress) {
                    KNetLogger.debug(TAG) { "KNet Proxy Provisional Response Headers: ${msg.status()}" }
                    lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                    return
                }
                timingCollector.markFirstByteReceived()

                responseStarted = true
                responseContentEncoding = HttpMapper.contentEncoding(msg.headers())
                capture?.observeResponse(HttpMapper.mapResponseHead(msg), Clock.System.now().toEpochMilliseconds())
                isKeepAlive = HttpUtil.isKeepAlive(msg)
                KNetLogger.info(TAG) { "KNet Proxy Response Headers: ${msg.status()}" }

                if (!isKeepAlive) {
                    msg.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                }
                lastClientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
            }

            is HttpContent -> {
                if (provisionalResponseInProgress) {
                    val provisionalWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                    lastClientWrite = provisionalWrite
                    if (msg is LastHttpContent) provisionalResponseInProgress = false
                    return
                }
                val chunk = msg.content()
                val readable = chunk.readableBytes()
                responseObservedBytes += readable.toLong()
                captureBodyChunk(
                    exchange = capture,
                    direction = TrafficDirection.SERVER_TO_CLIENT,
                    content = chunk,
                    contentEncoding = responseContentEncoding,
                )
                val clientWrite = clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                lastClientWrite = clientWrite


                if (msg is LastHttpContent) {
                    timingCollector.markLastByteReceived()

                    val timings = timingCollector.getTimings()
                    val completedAt = Clock.System.now().toEpochMilliseconds()
                    capture?.completeBody(
                        direction = TrafficDirection.SERVER_TO_CLIENT,
                        observedBytes = responseObservedBytes,
                        occurredAtEpochMillis = completedAt,
                    )
                    capture?.terminate(
                        state = ExchangeState.COMPLETED,
                        timings = timings,
                        occurredAtEpochMillis = completedAt,
                    )

                    responseComplete = true
                    clientWrite.addListener {
                        publishCompletion()
                        if (!isKeepAlive) {
                            clientChannel.close()
                        }
                        // The current upstream implementation is intentionally one-shot. Centralized
                        // bounded reuse can be added behind this ownership point after ordering tests.
                        context.close()
                    }
                }
            }
        }
    }

    /**
     * Couples upstream reads to completion of the latest downstream write for the decoded batch.
     * Netty may emit several HTTP objects for one socket read; advancing once at read-complete avoids
     * multiplying outstanding reads while retaining codec-sized, rather than message-sized, memory.
     */
    override fun channelReadComplete(context: ChannelHandlerContext) {
        val downstreamWrite = lastClientWrite
        lastClientWrite = null
        if (!responseComplete) {
            if (downstreamWrite == null) {
                context.read()
            } else {
                downstreamWrite.addListener { future ->
                    if (future.isSuccess && context.channel().isActive) {
                        context.read()
                    } else if (!future.isSuccess) {
                        context.close()
                    }
                }
            }
        }
        super.channelReadComplete(context)
    }

    override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
        if (cause is java.io.IOException) {
            KNetLogger.debug(TAG) { "KNet Outbound IO Exception (normal connection close): ${cause.message}" }
        } else {
            KNetLogger.error(TAG, cause) { "KNet Outbound Exception: ${cause.message}" }
        }

        if (!responseStarted) {
            val causeMessage = cause.message ?: "Outbound Proxy Exception: ${cause::class.simpleName}"
            val errBodyBytes = "502 Bad Gateway: $causeMessage".toByteArray(Charsets.UTF_8)
            val errResponse = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.BAD_GATEWAY,
                Unpooled.copiedBuffer(errBodyBytes)
            )
            errResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
            errResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errBodyBytes.size)

            val failedAt = Clock.System.now().toEpochMilliseconds()
            capture?.observeResponse(HttpMapper.mapResponseHead(errResponse), failedAt)
            captureBodyChunk(
                exchange = capture,
                direction = TrafficDirection.SERVER_TO_CLIENT,
                content = errResponse.content(),
                contentEncoding = HttpMapper.contentEncoding(errResponse.headers()),
            )
            capture?.completeBody(
                direction = TrafficDirection.SERVER_TO_CLIENT,
                observedBytes = errResponse.content().readableBytes().toLong(),
                occurredAtEpochMillis = failedAt,
            )
            capture?.terminate(
                state = ExchangeState.FAILED,
                timings = timingCollector.getTimings(),
                occurredAtEpochMillis = failedAt,
                errorCode = OUTBOUND_FAILURE,
            )
            clientChannel.writeAndFlush(errResponse).addListener {
                publishCompletion()
                clientChannel.close()
            }
        } else {
            capture?.cancelBody(
                direction = TrafficDirection.SERVER_TO_CLIENT,
                observedBytes = responseObservedBytes,
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                errorCode = OUTBOUND_FAILURE,
            )
            capture?.terminate(
                state = ExchangeState.FAILED,
                timings = timingCollector.getTimings(),
                occurredAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                errorCode = OUTBOUND_FAILURE,
            )
            publishCompletion()
            clientChannel.close()
        }

        context.close()
    }

    /** Publishes one terminal callback even when write, close, and exception events race. */
    private fun publishCompletion() {
        if (completionPublished.compareAndSet(false, true)) {
            onExchangeComplete()
        }
    }

}

/** Copies only bytes admitted by the capture sink and never changes the source buffer indices. */
internal fun captureBodyChunk(
    exchange: ProxyExchangeCapture?,
    direction: TrafficDirection,
    content: ByteBuf,
    contentEncoding: ContentEncoding?,
) {
    if (exchange == null || !content.isReadable) return
    var sourceOffset = 0
    val observedBytes = content.readableBytes()
    while (sourceOffset < observedBytes) {
        val reservation = exchange.tryReserveBody(
            direction = direction,
            contentEncoding = contentEncoding,
            requestedBytes = observedBytes - sourceOffset,
        ) ?: return
        val destination = reservation.writableBytes
        try {
            content.getBytes(content.readerIndex() + sourceOffset, destination)
            sourceOffset += destination.size
            if (!reservation.publish(Clock.System.now().toEpochMilliseconds())) return
        } catch (failure: Throwable) {
            reservation.cancel()
            throw failure
        }
    }
}
