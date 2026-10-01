package com.devuloopers.knet.engine.proxy.tls

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ssl.SniCompletionEvent
import io.netty.handler.ssl.SslHandshakeCompletionEvent

/** Records at most one TLS interception failure before any HTTP exchange can exist. */
internal class TlsInspectionOutcomeHandler(
    private val onFailure: () -> Unit,
) : ChannelInboundHandlerAdapter() {
    private var failureRecorded: Boolean = false

    override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
        val failed = when (event) {
            is SniCompletionEvent -> event.cause() != null
            is SslHandshakeCompletionEvent -> !event.isSuccess
            else -> false
        }
        if (failed && !failureRecorded) {
            failureRecorded = true
            onFailure()
        }
        context.fireUserEventTriggered(event)
    }
}
