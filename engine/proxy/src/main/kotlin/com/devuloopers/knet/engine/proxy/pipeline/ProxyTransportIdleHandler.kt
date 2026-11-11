package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.timeout.ReadTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Closes silent transports, preserving streams making progress in either direction and bounded intentional holds.
 * A download must not time out merely because its client has finished uploading. The separate WriteTimeoutHandler
 * still bounds an actual transport write stalled by an unresponsive peer.
 */
class ProxyTransportIdleHandler(timeout: Long, unit: TimeUnit) : IdleStateHandler(0L, 0L, timeout, unit) {
    override fun channelIdle(context: ChannelHandlerContext, event: IdleStateEvent) {
        if (event.state() != IdleState.ALL_IDLE) return
        if (ProxyTransportActivity.hasPending(context.channel())) return
        context.fireExceptionCaught(ReadTimeoutException.INSTANCE)
        context.close()
    }
}
