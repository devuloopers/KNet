package com.devuloopers.knet.engine.proxy.tls

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ssl.SniCompletionEvent
import io.netty.handler.ssl.SslHandshakeCompletionEvent

/** Records at most one TLS interception failure before any HTTP exchange can exist. */
internal class TlsInspectionOutcomeHandler(
    private val evidence: DownstreamTlsEvidence = DownstreamTlsEvidence(),
    private val onFailure: (TlsInspectionFailure) -> Unit,
) : ChannelInboundHandlerAdapter() {
    private var failureRecorded: Boolean = false
    private var certificateContextInstalled: Boolean = false

    override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
        if (event is SniCompletionEvent && event.cause() == null) certificateContextInstalled = true
        val failed = when (event) {
            is SniCompletionEvent -> event.cause() != null
            is SslHandshakeCompletionEvent -> !event.isSuccess
            else -> false
        }
        if (failed && !failureRecorded) {
            failureRecorded = true
            val cause = when (event) {
                is SniCompletionEvent -> event.cause()
                is SslHandshakeCompletionEvent -> event.cause()
                else -> null
            }
            onFailure(TlsInspectionFailureClassifier.classify(cause, certificateContextInstalled, evidence.presentation))
        }
        context.fireUserEventTriggered(event)
    }
}
