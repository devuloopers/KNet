package com.devuloopers.knet.connectivity.desktop.gateway

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.util.concurrent.Semaphore

/** Bounds physical TLS/keep-alive connections independently of authenticated WSS and short-operation admission. */
internal class ControlConnectionAdmission(private val permits: Semaphore) : ChannelInboundHandlerAdapter() {
    private var initialized = false
    override fun handlerAdded(context: ChannelHandlerContext) {
        if (initialized) return
        initialized = true
        if (!permits.tryAcquire()) {
            context.close()
            return
        }
        // The close future survives HTTP-to-WebSocket pipeline changes and releases exactly once.
        context.channel().closeFuture().addListener { permits.release() }
    }
}
