package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.util.ReferenceCountUtil
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Real timer tests for silent sockets, one-way progress and bounded local/peer pause ownership. */
class ProxyTransportIdleHandlerTest {
    @Test fun `a genuinely silent connection still times out`() {
        val errors = mutableListOf<Throwable>()
        val channel = channel(errors)
        try {
            tick(channel)
            assertFalse(channel.isActive)
            assertTrue(errors.single() is ReadTimeoutException)
        } finally { channel.finishAndReleaseAll() }
    }

    @Test fun `outbound progress keeps a download alive without inbound application bytes`() {
        val errors = mutableListOf<Throwable>()
        val channel = channel(errors)
        try {
            repeat(5) {
                channel.writeOutbound(Unpooled.wrappedBuffer(byteArrayOf(1)))
                ReferenceCountUtil.release(channel.readOutbound<Any>())
                Thread.sleep(35)
                channel.runScheduledPendingTasks()
                assertTrue(channel.isActive)
            }
            assertTrue(errors.isEmpty())
            tick(channel)
            assertFalse(channel.isActive)
        } finally { channel.finishAndReleaseAll() }
    }

    @Test fun `an owned pause defers inactivity and releases exactly once`() {
        val errors = mutableListOf<Throwable>()
        val channel = channel(errors)
        val hold = ProxyTransportActivity.hold(channel)
        try {
            tick(channel)
            assertTrue(channel.isActive)
            hold.close(); hold.close()
            assertFalse(ProxyTransportActivity.hasPending(channel))
            tick(channel)
            assertFalse(channel.isActive)
        } finally { hold.close(); channel.finishAndReleaseAll() }
    }

    @Test fun `a linked raw peer retains the connection while shaped delivery remains pending`() {
        val errors = mutableListOf<Throwable>()
        val channel = channel(errors)
        val peer = EmbeddedChannel()
        ProxyTransportActivity.link(channel, peer)
        val hold = ProxyTransportActivity.hold(peer)
        try {
            tick(channel)
            assertTrue(channel.isActive)
            hold.close()
            tick(channel)
            assertFalse(channel.isActive)
        } finally { hold.close(); channel.finishAndReleaseAll(); peer.finishAndReleaseAll() }
    }

    @Test fun `resuming a shaped upload gives its origin a fresh response interval`() {
        val errors = mutableListOf<Throwable>()
        val channel = channel(errors)
        val hold = ProxyTransportActivity.hold(channel)
        try {
            Thread.sleep(150)
            hold.close()
            // The old deadline has expired, but the final queued upload has only just been released.
            channel.runScheduledPendingTasks()
            assertTrue(channel.isActive)
            assertTrue(errors.isEmpty())
            tick(channel)
            assertFalse(channel.isActive)
        } finally { hold.close(); channel.finishAndReleaseAll() }
    }

    private fun channel(errors: MutableList<Throwable>): EmbeddedChannel = EmbeddedChannel(
        ProxyTransportIdleHandler(100, TimeUnit.MILLISECONDS),
        object : ChannelInboundHandlerAdapter() {
            override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) { errors += cause }
        },
    )

    private fun tick(channel: EmbeddedChannel) {
        Thread.sleep(150)
        channel.runScheduledPendingTasks()
    }
}
