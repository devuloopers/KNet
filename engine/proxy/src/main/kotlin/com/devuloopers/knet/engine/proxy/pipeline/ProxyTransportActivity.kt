package com.devuloopers.knet.engine.proxy.pipeline

import io.netty.channel.Channel
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.util.AttributeKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Neutral transport contract for bounded intentional pauses, independent of the simulator or breakpoint module. */
object ProxyTransportActivity {
    private val holds = AttributeKey.valueOf<AtomicInteger>("knet.intentionalIoHolds")
    private val peer = AttributeKey.valueOf<Channel>("knet.duplexActivityPeer")

    /** Keeps read-inactivity deadlines from treating an owned, bounded delay as a silent remote endpoint. */
    fun hold(channel: Channel): AutoCloseable {
        val channels = listOfNotNull(channel, channel.parent().takeIf { channel is Http2StreamChannel }).distinct()
        val counters = channels.map { owner ->
            val candidate = AtomicInteger()
            owner to (owner.attr(holds).setIfAbsent(candidate) ?: candidate).also { it.incrementAndGet() }
        }
        val released = AtomicBoolean(false)
        return AutoCloseable {
            if (released.compareAndSet(false, true)) counters.forEach { (owner, counter) ->
                if (counter.decrementAndGet() == 0) {
                    // A timer can expire between the final queued upload and the origin's first response.
                    // Start a fresh remote-inactivity interval when local intentional work finishes.
                    resumeIdleDeadline(owner)
                    owner.attr(peer).get()?.let(::resumeIdleDeadline)
                }
            }
        }
    }

    private fun resumeIdleDeadline(channel: Channel) {
        val reset = Runnable {
            channel.pipeline().get(ProxyTransportIdleHandler::class.java)?.let { handler ->
                handler.resetReadTimeout()
                handler.resetWriteTimeout()
            }
        }
        if (channel.eventLoop().inEventLoop()) reset.run() else channel.eventLoop().execute(reset)
    }

    /** Links the two halves of one raw tunnel so queued peer delivery also prevents premature idle close. */
    fun link(first: Channel, second: Channel) {
        first.attr(peer).set(second)
        second.attr(peer).set(first)
    }

    /** True only while an owner in this channel or its linked tunnel peer retains intentional work. */
    fun hasPending(channel: Channel): Boolean = (channel.attr(holds).get()?.get() ?: 0) > 0 ||
        (channel.attr(peer).get()?.attr(holds)?.get()?.get() ?: 0) > 0
}
