package com.devuloopers.knet.engine.proxy.tls

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.ssl.AbstractSniHandler
import io.netty.util.concurrent.Future

/**
 * Holds at most one configured ClientHello while an SNI-aware transport route is prepared.
 *
 * Removing or replacing this decoder forwards its unread cumulation exactly once to the newly
 * installed TLS or raw relay handler, preserving the original encrypted bytes.
 */
internal class TlsClientHelloPolicyHandler<R>(
    maximumClientHelloBytes: Int,
    handshakeTimeoutMillis: Long,
    private val lookupRoute: (ChannelHandlerContext, TlsClientHelloMetadata) -> Future<R>,
    private val installRoute: (ChannelHandlerContext, R, TlsClientHelloMetadata) -> Unit,
) : AbstractSniHandler<R>(maximumClientHelloBytes, handshakeTimeoutMillis) {
    private var parsedMetadata: TlsClientHelloMetadata = TlsClientHelloMetadata(null, emptyList(), emptyList())

    override fun lookup(context: ChannelHandlerContext, clientHello: ByteBuf?): Future<R> {
        parsedMetadata = clientHello?.let(TlsClientHelloMetadataParser::parse)
            ?: TlsClientHelloMetadata(null, emptyList(), emptyList())
        return super.lookup(context, clientHello)
    }

    override fun lookup(context: ChannelHandlerContext, hostname: String?): Future<R> {
        val metadata = parsedMetadata.copy(serverName = hostname?.takeIf(String::isNotBlank))
        parsedMetadata = metadata
        return lookupRoute(context, metadata)
    }

    override fun onLookupComplete(context: ChannelHandlerContext, hostname: String?, future: Future<R>) {
        if (!future.isSuccess) {
            context.fireExceptionCaught(future.cause())
            context.close()
            return
        }
        installRoute(context, future.getNow(), parsedMetadata)
    }
}
