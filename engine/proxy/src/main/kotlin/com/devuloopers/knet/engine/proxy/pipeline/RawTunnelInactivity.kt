package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.channel.ChannelPipeline
import java.util.concurrent.TimeUnit

/**
 * Replaces the setup/HTTP deadline once a connection becomes an opaque raw tunnel.
 * @param pipeline Connected channel pipeline, mutated on its owner event loop.
 * @param timeoutMillis Explicit raw inactivity limit; zero permits an idle persistent connection.
 * Write deadlines and connection admission remain owned by their existing handlers/controllers.
 */
internal fun configureRawTunnelInactivity(pipeline: ChannelPipeline, timeoutMillis: Long) {
    require(timeoutMillis >= 0L)
    pipeline.get(PipelineHandlerNames.READ_TIMEOUT)?.let(pipeline::remove)
    if (timeoutMillis == 0L) return
    val handler = ProxyTransportIdleHandler(timeoutMillis, TimeUnit.MILLISECONDS)
    if (pipeline.get(PipelineHandlerNames.DUPLEX_RELAY) != null) {
        pipeline.addBefore(PipelineHandlerNames.DUPLEX_RELAY, PipelineHandlerNames.READ_TIMEOUT, handler)
    } else {
        pipeline.addLast(PipelineHandlerNames.READ_TIMEOUT, handler)
    }
}
