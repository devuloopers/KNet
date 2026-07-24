package com.devuloopers.knet.simulator

import com.devuloopers.knet.logger.KNetLogger
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
 * Netty [ChannelDuplexHandler] that applies network condition simulation on a per-channel basis.
 *
 * This handler must be registered **before** `proxyHandler` in the pipeline via
 * `KNetProxyServer.pipelineInitializers`. It reads the active [NetworkProfile] from
 * [NetworkSimulatorManager] on every request and response, allowing hot-swapping
 * the simulation profile without restarting the proxy.
 *
 * ## Simulation Axes
 *
 * ### Packet Loss (inbound, channelRead)
 * When [NetworkProfile.packetLossPercent] > 0, a random integer in [0, 100) is compared
 * against the threshold. If the roll falls below the threshold, the message is silently
 * dropped via [ReferenceCountUtil.release] and no further pipeline stages are notified.
 * This simulates TCP-level packet loss from the client's perspective.
 *
 * ### Latency (inbound & outbound)
 * When [NetworkProfile.latencyMs] > 0, message forwarding is deferred by scheduling
 * the pipeline continuation on the channel's `EventExecutor` using a fixed delay.
 * This avoids blocking Netty's I/O threads — the event loop thread parks the task in
 * its scheduler queue and picks it up after the delay expires.
 *
 * ### Bandwidth Throttling (inbound & outbound)
 * When [NetworkProfile.bandwidthBytesPerSecond] is set, a [ChannelTrafficShapingHandler]
 * is dynamically inserted at the head of this channel's pipeline. When the profile
 * changes to one without a bandwidth limit, the handler is removed.
 * Using `ChannelTrafficShapingHandler` (per-channel) ensures each proxied connection
 * is independently throttled, accurately simulating a single device's network interface.
 *
 * @property manager The [NetworkSimulatorManager] providing the active [NetworkProfile].
 */
@ChannelHandler.Sharable
class KNetNetworkSimulatorHandler(
    private val manager: NetworkSimulatorManager
) : ChannelDuplexHandler() {

    companion object {
        /** Pipeline name key for the per-channel traffic shaping handler. */
        private const val TRAFFIC_SHAPER_NAME = "knet.trafficShaper"
    }

    /**
     * Intercepts inbound HTTP messages to apply packet loss and latency simulation.
     *
     * Execution order:
     * 1. Packet loss check — if the message is dropped, no further processing occurs.
     * 2. Latency delay — the forwarding call is scheduled after the configured delay.
     * 3. Bandwidth — handled passively by [ChannelTrafficShapingHandler] already in the pipeline.
     */
    override fun channelRead(context: ChannelHandlerContext, msg: Any) {
        val profile = manager.activeProfile

        // --- Packet loss ---
        if (profile.packetLossPercent > 0) {
            val roll = Random.nextInt(100)
            if (roll < profile.packetLossPercent) {
                KNetLogger.debug(TAG) {
                    "Packet dropped (loss=${profile.packetLossPercent}%, roll=$roll)"
                }
                ReferenceCountUtil.release(msg)
                return
            }
        }

        // --- Latency ---
        if (profile.latencyMs > 0) {
            // Retain the message before scheduling to prevent premature deallocation.
            ReferenceCountUtil.retain(msg)
            context.executor().schedule({
                try {
                    context.fireChannelRead(msg)
                } finally {
                    // Release our extra retain — fireChannelRead passes ownership to the next handler.
                    ReferenceCountUtil.release(msg)
                }
            }, profile.latencyMs, TimeUnit.MILLISECONDS)
        } else {
            context.fireChannelRead(msg)
        }
    }

    /**
     * Intercepts outbound HTTP writes to apply latency simulation.
     *
     * Bandwidth throttling on the outbound path is handled automatically by
     * [ChannelTrafficShapingHandler] when it is present in the pipeline.
     */
    override fun write(context: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        val profile = manager.activeProfile

        if (profile.latencyMs > 0) {
            ReferenceCountUtil.retain(msg)
            context.executor().schedule({
                try {
                    context.write(msg, promise)
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            }, profile.latencyMs, TimeUnit.MILLISECONDS)
        } else {
            context.write(msg, promise)
        }
    }

    /**
     * Called when the channel becomes active (connection established).
     * Installs the [ChannelTrafficShapingHandler] at the pipeline head if the active
     * profile has a bandwidth limit configured.
     */
    override fun channelActive(context: ChannelHandlerContext) {
        syncTrafficShaper(context)
        context.fireChannelActive()
    }

    /**
     * Synchronizes the presence of [ChannelTrafficShapingHandler] in the pipeline
     * to match the bandwidth setting of the currently active [NetworkProfile].
     *
     * - If a bandwidth limit is active and no shaper exists, one is inserted before this handler.
     * - If no bandwidth limit is active but a shaper exists, it is removed.
     *
     * @param context The Netty channel handler context whose pipeline is mutated.
     */
    private fun syncTrafficShaper(context: ChannelHandlerContext) {
        val profile = manager.activeProfile
        val pipeline = context.pipeline()
        val existingShaper = pipeline.get(TRAFFIC_SHAPER_NAME)

        if (profile.bandwidthBytesPerSecond != null) {
            if (existingShaper == null) {
                // Insert a per-channel traffic shaper. writeLimit and readLimit are in bytes/sec.
                val shaper = ChannelTrafficShapingHandler(
                    /* writeLimit = */ profile.bandwidthBytesPerSecond,
                    /* readLimit  = */ profile.bandwidthBytesPerSecond
                )
                pipeline.addBefore(context.name(), TRAFFIC_SHAPER_NAME, shaper)
                KNetLogger.debug(TAG) {
                    "Installed ChannelTrafficShapingHandler: ${profile.bandwidthBytesPerSecond} B/s"
                }
            }
        } else {
            if (existingShaper != null) {
                pipeline.remove(TRAFFIC_SHAPER_NAME)
                KNetLogger.debug(TAG) { "Removed ChannelTrafficShapingHandler (no bandwidth limit)" }
            }
        }
    }
}
