package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.core.logger.KNetLogger
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.traffic.ChannelTrafficShapingHandler
import io.netty.util.ReferenceCountUtil
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val TAG = "KNetNetworkSimulatorHandler"

/**
 * Netty [ChannelDuplexHandler] applying network condition simulation per channel.
 *
 * @property manager Provides the active [NetworkProfile].
 * @property stats Tracks runtime simulation metrics.
 * @property random Random source for probabilistic packet loss (injectable for deterministic tests).
 */
@ChannelHandler.Sharable
class KNetNetworkSimulatorHandler(
    private val manager: NetworkSimulatorManager,
    private val stats: NetworkSimulationStats = NetworkSimulationStats(),
    private val random: Random = Random.Default
) : ChannelDuplexHandler() {

    companion object {
        private const val TRAFFIC_SHAPER_NAME = "knet.trafficShaper"
    }

    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        syncTrafficShaper(context)
        val profile = manager.activeProfile

        // --- Packet Loss ---
        if (profile.packetLossPercent > 0) {
            val roll = random.nextInt(100)
            if (roll < profile.packetLossPercent) {
                stats.incrementPacketsDropped()
                KNetLogger.debug(TAG) { "Packet dropped (loss=${profile.packetLossPercent}%, roll=$roll)" }
                ReferenceCountUtil.release(msg)
                return
            }
        }

        // --- Latency ---
        if (profile.latencyMs > 0) {
            stats.incrementPacketsDelayed()
            ReferenceCountUtil.retain(msg)
            context.executor().schedule({
                try {
                    if (context.channel().isActive) {
                        context.fireChannelRead(msg)
                    }
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            }, profile.latencyMs, TimeUnit.MILLISECONDS)
        } else {
            context.fireChannelRead(msg)
        }
    }

    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        syncTrafficShaper(context)
        val profile = manager.activeProfile

        if (profile.latencyMs > 0) {
            stats.incrementPacketsDelayed()
            ReferenceCountUtil.retain(msg)
            context.executor().schedule({
                try {
                    if (context.channel().isActive) {
                        context.write(msg, promise)
                    }
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            }, profile.latencyMs, TimeUnit.MILLISECONDS)
        } else {
            context.write(msg, promise)
        }
    }

    override fun channelActive(context: ChannelHandlerContext) {
        syncTrafficShaper(context)
        context.fireChannelActive()
    }

    private fun syncTrafficShaper(context: ChannelHandlerContext) {
        val profile = manager.activeProfile
        val pipeline = context.pipeline()
        val existingShaper = pipeline.get(TRAFFIC_SHAPER_NAME) as? ChannelTrafficShapingHandler

        val targetBps = profile.bandwidthBytesPerSecond
        if (targetBps != null) {
            if (existingShaper == null) {
                val shaper = ChannelTrafficShapingHandler(targetBps, targetBps)
                pipeline.addBefore(context.name(), TRAFFIC_SHAPER_NAME, shaper)
            }
        } else {
            if (existingShaper != null) {
                pipeline.remove(TRAFFIC_SHAPER_NAME)
            }
        }
    }
}
