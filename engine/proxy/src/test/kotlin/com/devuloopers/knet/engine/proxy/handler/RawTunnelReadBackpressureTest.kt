package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.traffic.model.TrafficDirection
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import kotlin.test.*

/** A completed read batch cannot drain another batch while its peer's shaped transport write is outstanding. */
class RawTunnelReadBackpressureTest {
    @Test fun `read completion waits for every admitted peer write before requesting more data`() {
        val pending = ArrayDeque<Pair<Any, ChannelPromise>>()
        val peer = EmbeddedChannel(object : ChannelOutboundHandlerAdapter() {
            override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
                pending.addLast(message to promise)
            }
        })
        var reads = 0
        val channel = EmbeddedChannel(object : ChannelOutboundHandlerAdapter() {
            override fun read(context: ChannelHandlerContext) { reads += 1; context.read() }
        }, KNetRawTunnelRelayHandler(peer, TrafficDirection.SERVER_TO_CLIENT, null, RawTunnelLifecycle(null)))
        assertFalse(channel.config().isAutoRead, "Raw routing must take read ownership even after an automatic TLS classifier")
        val baseline = reads
        fun finishWrite() {
            val (message, promise) = pending.removeFirst()
            ReferenceCountUtil.release(message); promise.setSuccess()
            channel.runPendingTasks()
        }
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1)), Unpooled.wrappedBuffer(byteArrayOf(2)))
            assertEquals(2, pending.size)
            assertEquals(baseline, reads, "channelReadComplete bypassed shaped-write backpressure")
            finishWrite()
            assertEquals(baseline, reads, "The first write cannot admit reads while a second write remains pending")
            finishWrite()
            assertEquals(baseline + 1, reads)
        } finally {
            while (pending.isNotEmpty()) finishWrite()
            channel.finishAndReleaseAll(); peer.finishAndReleaseAll()
        }
    }
}
