package com.devuloopers.knet.engine.proxy.tls

import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.ssl.SniHandler
import io.netty.handler.ssl.SslContext
import io.netty.util.AsyncMapping
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor

/** Creates per-tunnel SNI handlers backed by asynchronous leaf-certificate generation. */
internal class SniTlsContextHandlerFactory(
    private val tlsContextProvider: ServerTlsContextProvider,
    private val certificateExecutor: Executor,
    private val maximumClientHelloBytes: Int,
    private val handshakeTimeoutMillis: Long,
) {
    init {
        require(maximumClientHelloBytes > 0)
        require(handshakeTimeoutMillis > 0L)
    }

    /** Creates one channel-confined handler using [connectHost] when the client omits SNI. */
    fun create(context: ChannelHandlerContext, connectHost: String): SniHandler = SniHandler(
        contextMapping(context, connectHost),
        maximumClientHelloBytes,
        handshakeTimeoutMillis,
    )

    /** Bridges provider completion back onto the channel event loop and its Netty promise. */
    private fun contextMapping(
        context: ChannelHandlerContext,
        connectHost: String,
    ): AsyncMapping<String, SslContext> = object : AsyncMapping<String, SslContext> {
        override fun map(input: String?, promise: Promise<SslContext>): Future<SslContext> {
            val serverName = runCatching { TlsServerName.select(input, connectHost) }
                .getOrElse { failure ->
                    promise.tryFailure(failure)
                    return promise
                }
            val resolution = runCatching {
                tlsContextProvider.resolve(serverName, certificateExecutor)
            }.getOrElse { failure ->
                promise.tryFailure(failure)
                return promise
            }
            resolution.whenComplete { sslContext, failure ->
                context.executor().execute {
                    when {
                        failure != null -> promise.tryFailure(unwrapCompletionFailure(failure))
                        sslContext == null -> promise.tryFailure(
                            IllegalStateException("TLS context provider returned no context."),
                        )
                        !context.channel().isActive -> promise.cancel(false)
                        else -> {
                            context.channel().attr(ProxyChannelAttributes.TLS_SERVER_NAME).set(serverName)
                            promise.trySuccess(sslContext)
                        }
                    }
                }
            }
            return promise
        }
    }

    private tailrec fun unwrapCompletionFailure(failure: Throwable): Throwable {
        val cause = (failure as? CompletionException)?.cause ?: return failure
        return unwrapCompletionFailure(cause)
    }
}
