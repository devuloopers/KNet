package com.devuloopers.knet.engine.proxy.upstream

import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.engine.proxy.ConnectionLease
import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.ProxyConnectionAdmissionController
import com.devuloopers.knet.engine.proxy.pipeline.PipelineHandlerNames
import com.devuloopers.knet.engine.proxy.ssl.ProxyTrustManager
import com.devuloopers.knet.engine.proxy.tls.KeyManagerProvider
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoop
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2GoAwayFrame
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.ReferenceCountUtil
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val HTTP_TWO_POOL_TAG: String = "ProxyEngine"

/** Immutable origin identity used to prevent cross-origin HTTP/2 connection reuse. */
internal data class HttpTwoUpstreamRoute(
    val host: String,
    val resolvedRoute: ResolvedUpstreamRoute,
) {
    val port: Int
        get() = resolvedRoute.port
}

/** Indicates that an origin completed TLS but selected HTTP/1 instead of HTTP/2. */
internal class HttpTwoNegotiationUnavailableException(message: String) : IOException(message)

/** Indicates that all bounded HTTP/2 parents for an origin have exhausted their stream budgets. */
internal class HttpTwoPoolSaturatedException(message: String) : IOException(message)

/**
 * Server-owned, origin-keyed HTTP/2 connection pool.
 *
 * Parent TCP/TLS channels are admitted once and reused. Every exchange receives an independent
 * [io.netty.handler.codec.http2.Http2StreamChannel], so cancellation, flow control, interception,
 * and capture ownership cannot leak across sibling streams. GOAWAY marks only that parent as
 * draining; new streams select or create a replacement within the configured bounds.
 */
internal class HttpTwoUpstreamConnectionPool(
    private val runtimePolicy: KNetProxyRuntimePolicy,
    private val admissionController: ProxyConnectionAdmissionController,
    private val keyManagerProvider: KeyManagerProvider?,
    private val verifyUpstreamTls: Boolean,
    private val happyEyeballsDialer: HappyEyeballsDialer = HappyEyeballsDialer(runtimePolicy),
) : AutoCloseable {
    private val lock = Any()
    private val entriesByOrigin = mutableMapOf<OriginKey, MutableList<PooledConnection>>()
    private var closed: Boolean = false

    /** Opens one stream, creating or reusing a bounded origin connection as required. */
    fun openStream(
        eventLoop: EventLoop,
        route: HttpTwoUpstreamRoute,
        streamInitializer: ChannelInitializer<Channel>,
    ): CompletableFuture<Channel> {
        val result = CompletableFuture<Channel>()
        // A transparent CONNECT tunnel can preserve one TLS hostname while DNS changes its
        // candidate set. Pooling retains both identities without depending on response order.
        val key = OriginKey(route.host, route.resolvedRoute.addressSetKey, route.port)
        val entry = synchronized(lock) {
            if (closed) {
                result.completeExceptionally(IOException("HTTP/2 upstream pool is closed."))
                return result
            }
            val entries = entriesByOrigin.getOrPut(key) { mutableListOf() }
            val reusable = entries.firstOrNull { candidate ->
                !candidate.draining &&
                    !candidate.ready.isCompletedExceptionally &&
                    candidate.reservedStreams < runtimePolicy.maximumHttp2ConcurrentStreams
            }
            val selected = reusable ?: run {
                val selectableParents = entries.count { candidate -> !candidate.draining }
                if (selectableParents >= runtimePolicy.maximumHttp2ConnectionsPerOrigin) {
                    result.completeExceptionally(
                        HttpTwoPoolSaturatedException(
                            "HTTP/2 stream capacity is exhausted for ${route.host}:${route.port}.",
                        ),
                    )
                    return result
                }
                createConnection(eventLoop, key, route).also(entries::add)
            }
            selected.reservedStreams += 1L
            selected
        }

        entry.ready.whenComplete { parent, connectionFailure ->
            if (connectionFailure != null || parent == null) {
                releaseReservation(key, entry)
                removeConnection(key, entry)
                result.completeExceptionally(connectionFailure ?: IOException("HTTP/2 parent unavailable."))
                return@whenComplete
            }
            if (entry.draining || !parent.isActive) {
                releaseReservation(key, entry)
                result.completeExceptionally(IOException("HTTP/2 parent is draining or closed."))
                return@whenComplete
            }

            Http2StreamChannelBootstrap(parent)
                .option(ChannelOption.AUTO_READ, false)
                .handler(streamInitializer)
                .open()
                .addListener { openFuture ->
                    if (!openFuture.isSuccess) {
                        releaseReservation(key, entry)
                        result.completeExceptionally(
                            openFuture.cause() ?: IOException("Failed to open an HTTP/2 stream."),
                        )
                        return@addListener
                    }
                    val stream = openFuture.getNow() as Channel
                    stream.closeFuture().addListener { releaseReservation(key, entry) }
                    result.complete(stream)
                }
        }
        return result
    }

    /** Creates one TLS parent and completes [PooledConnection.ready] only after ALPN selects h2. */
    private fun createConnection(
        eventLoop: EventLoop,
        key: OriginKey,
        route: HttpTwoUpstreamRoute,
    ): PooledConnection {
        val ready = CompletableFuture<Channel>()
        val lease = admissionController.tryAcquireUpstream()
        val entry = PooledConnection(ready = ready, admissionLease = lease)
        if (lease == null) {
            ready.completeExceptionally(IOException("Global upstream connection limit is saturated."))
            return entry
        }

        happyEyeballsDialer.connect(eventLoop, route.resolvedRoute).whenComplete { connected, failure ->
            eventLoop.execute {
                if (entry.ready.isDone) {
                    connected?.channel?.close()
                    return@execute
                }
                if (failure != null || connected == null) {
                    failConnection(
                        key,
                        entry,
                        failure ?: IOException("HTTP/2 upstream TCP connection failed."),
                    )
                    return@execute
                }
                val channel = connected.channel as SocketChannel
                entry.parent = channel
                configureConnectedChannel(key, entry, route, channel)
                channel.closeFuture().addListener {
                    removeConnection(key, entry)
                    ready.completeExceptionally(IOException("HTTP/2 upstream parent closed."))
                }
                channel.config().isAutoRead = true
            }
        }
        return entry
    }

    /** Adds TLS/ALPN only after one dual-stack TCP candidate has won the race. */
    private fun configureConnectedChannel(
        key: OriginKey,
        entry: PooledConnection,
        route: HttpTwoUpstreamRoute,
        channel: SocketChannel,
    ) {
        val pipeline = channel.pipeline()
        pipeline.addLast(
            PipelineHandlerNames.READ_TIMEOUT,
            ReadTimeoutHandler(runtimePolicy.readIdleTimeoutMillis, TimeUnit.MILLISECONDS),
        )
        pipeline.addLast(
            PipelineHandlerNames.WRITE_TIMEOUT,
            WriteTimeoutHandler(runtimePolicy.writeIdleTimeoutMillis, TimeUnit.MILLISECONDS),
        )
        val sslBuilder = SslContextBuilder.forClient()
            .trustManager(ProxyTrustManager.getTrustManagerFactory(verifyUpstreamTls))
            .applicationProtocolConfig(
                ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1,
                ),
            )
        keyManagerProvider?.getKeyManagerFactory(route.host)?.let(sslBuilder::keyManager)
        val sslHandler = sslBuilder.build()
            .newHandler(channel.alloc(), route.host, route.port)
            .apply { setHandshakeTimeoutMillis(runtimePolicy.tlsHandshakeTimeoutMillis) }
        sslHandler.handshakeFuture().addListener { handshake ->
            if (!handshake.isSuccess) {
                failConnection(
                    key,
                    entry,
                    handshake.cause() ?: IOException("HTTP/2 upstream TLS handshake failed."),
                )
            }
        }
        pipeline.addLast(PipelineHandlerNames.SSL, sslHandler)
        pipeline.addLast(
            PipelineHandlerNames.ALPN,
            object : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                override fun configurePipeline(context: ChannelHandlerContext, protocol: String) {
                    if (protocol != ApplicationProtocolNames.HTTP_2) {
                        failConnection(
                            key,
                            entry,
                            HttpTwoNegotiationUnavailableException(
                                "Origin ${route.host}:${route.port} negotiated ${protocol.ifBlank { "HTTP/1.1" }}.",
                            ),
                        )
                        return
                    }
                    val settings = Http2Settings()
                        .pushEnabled(false)
                        .maxHeaderListSize(runtimePolicy.maximumHttp2HeaderListBytes)
                        .initialWindowSize(runtimePolicy.http2InitialWindowBytes)
                    val frameCodec = Http2FrameCodecBuilder.forClient()
                        .initialSettings(settings)
                        .gracefulShutdownTimeoutMillis(runtimePolicy.gracefulShutdownTimeoutMillis)
                        .build()
                    context.pipeline().addAfter(
                        PipelineHandlerNames.ALPN,
                        PipelineHandlerNames.HTTP2_CODEC,
                        frameCodec,
                    )
                    context.pipeline().addAfter(
                        PipelineHandlerNames.HTTP2_CODEC,
                        HTTP_TWO_LIFECYCLE_HANDLER,
                        HttpTwoParentLifecycleHandler { markDraining(key, entry) },
                    )
                    context.pipeline().addAfter(
                        HTTP_TWO_LIFECYCLE_HANDLER,
                        PipelineHandlerNames.HTTP2_MULTIPLEX,
                        Http2MultiplexHandler(RejectServerPushHandler()),
                    )
                    entry.ready.complete(context.channel())
                }
            },
        )
    }

    /** Stops assigning new streams after GOAWAY and closes the parent after its last child. */
    private fun markDraining(key: OriginKey, entry: PooledConnection) {
        val closeNow = synchronized(lock) {
            entry.draining = true
            entry.reservedStreams == 0L
        }
        KNetLogger.debug(HTTP_TWO_POOL_TAG) { "HTTP/2 upstream parent is draining for ${key.host}:${key.port}." }
        if (closeNow) entry.parent?.close()
    }

    /** Releases exactly one stream reservation and finishes a draining parent when empty. */
    private fun releaseReservation(key: OriginKey, entry: PooledConnection) {
        val closeNow = synchronized(lock) {
            entry.reservedStreams = (entry.reservedStreams - 1L).coerceAtLeast(0L)
            entry.draining && entry.reservedStreams == 0L
        }
        if (closeNow) entry.parent?.close()
        removeEmptyOrigin(key)
    }

    private fun failConnection(key: OriginKey, entry: PooledConnection, failure: Throwable) {
        entry.ready.completeExceptionally(failure)
        entry.parent?.close()
        removeConnection(key, entry)
    }

    private fun removeConnection(key: OriginKey, entry: PooledConnection) {
        val removed = synchronized(lock) {
            val entries = entriesByOrigin[key]
            val didRemove = entries?.remove(entry) == true
            if (entries?.isEmpty() == true) entriesByOrigin.remove(key)
            didRemove
        }
        if (removed) entry.admissionLease?.close()
    }

    private fun removeEmptyOrigin(key: OriginKey) {
        synchronized(lock) {
            if (entriesByOrigin[key]?.isEmpty() == true) entriesByOrigin.remove(key)
        }
    }

    /** Closes all parents; active stream children terminate through their normal channel callbacks. */
    override fun close() {
        val entries = synchronized(lock) {
            if (closed) return
            closed = true
            entriesByOrigin.values.flatten().also { entriesByOrigin.clear() }
        }
        entries.forEach { entry ->
            entry.ready.completeExceptionally(IOException("HTTP/2 upstream pool closed."))
            entry.parent?.close()
            entry.admissionLease?.close()
        }
    }

    private data class OriginKey(
        val host: String,
        val resolvedAddressSet: List<String>,
        val port: Int,
    )

    private class PooledConnection(
        val ready: CompletableFuture<Channel>,
        val admissionLease: ConnectionLease?,
        var parent: Channel? = null,
        var reservedStreams: Long = 0L,
        var draining: Boolean = false,
    )

    /** Observes connection-level GOAWAY without consuming frame ownership. */
    private class HttpTwoParentLifecycleHandler(
        private val onGoAway: () -> Unit,
    ) : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            if (message is Http2GoAwayFrame) onGoAway()
            context.fireChannelRead(message)
        }
    }

    /** Rejects unsolicited server-push streams; KNet never creates hidden traffic from push promises. */
    private class RejectServerPushHandler : ChannelInboundHandlerAdapter() {
        override fun channelRead(context: ChannelHandlerContext, message: Any) {
            ReferenceCountUtil.release(message)
            context.close()
        }
    }

    private companion object {
        const val HTTP_TWO_LIFECYCLE_HANDLER: String = "http2PoolLifecycle"
    }
}
