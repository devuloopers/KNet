package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import com.devuloopers.knet.engine.proxy.pipeline.ProxyTransportActivity
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.channel.socket.ChannelInputShutdownReadComplete
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** A TCP FIN must remain behind the bytes that the simulator has deliberately delayed. */
class ShapedInputShutdownTest {
    @Test fun `input EOF follows the last scheduled upload bytes`() = exercise(disable = false)
    @Test fun `live disable releases upload bytes before its deferred EOF`() = exercise(disable = true)

    @Test fun `manual read requests wait until the delayed upload batch drains`() {
        val profile = NetworkConditionProfile(NetworkConditionProfileId("read-fence"), "Read fence", latencyMillis = 100)
        var now = 0L
        val engine = NetworkConditionEngine({ NetworkConditionConfiguration(enabled = true,
            globalProfileId = profile.id, customProfiles = listOf(profile)) }, nanoTime = { now })
        var reads = 0
        val channel = EmbeddedChannel(object : ChannelOutboundHandlerAdapter() {
            override fun read(context: ChannelHandlerContext) { reads += 1; context.read() }
        }, NetworkConditionChannelHandler(engine))
        channel.config().isAutoRead = false
        channel.attr(ProxyChannelAttributes.ROUTE_HOST).set("upload.example.test")
        channel.attr(ProxyChannelAttributes.PORT).set(443)
        val baseline = reads
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))
            repeat(3) { channel.read() }
            assertEquals(baseline, reads)
            now = TimeUnit.MILLISECONDS.toNanos(200)
            channel.advanceTimeBy(200, TimeUnit.MILLISECONDS)
            channel.runScheduledPendingTasks()
            assertEquals(baseline + 1, reads)
            channel.readInbound<ByteBuf>().release()
        } finally { channel.finishAndReleaseAll() }
    }

    private fun exercise(disable: Boolean) {
        val profile = NetworkConditionProfile(NetworkConditionProfileId("delayed-upload"), "Delayed upload", latencyMillis = 100)
        val configuration = MutableStateFlow(NetworkConditionConfiguration(enabled = true, globalProfileId = profile.id, customProfiles = listOf(profile)))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var now = 0L
        val engine = NetworkConditionEngine(configuration, scope, nanoTime = { now })
        val events = mutableListOf<String>()
        val channel = EmbeddedChannel(NetworkConditionChannelHandler(engine), object : ChannelInboundHandlerAdapter() {
            override fun channelRead(context: ChannelHandlerContext, message: Any) {
                events += "payload"
                (message as ByteBuf).release()
            }
            override fun userEventTriggered(context: ChannelHandlerContext, event: Any) {
                events += when (event) {
                    ChannelInputShutdownEvent.INSTANCE -> "eof"
                    ChannelInputShutdownReadComplete.INSTANCE -> "read-complete"
                    else -> error("Unexpected terminal event")
                }
            }
        })
        channel.attr(ProxyChannelAttributes.ROUTE_HOST).set("upload.example.test")
        channel.attr(ProxyChannelAttributes.PORT).set(443)
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1, 2, 3)))
            channel.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
            channel.pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE)
            assertTrue(events.isEmpty())
            if (disable) {
                configuration.value = configuration.value.copy(enabled = false)
                channel.runPendingTasks()
            } else {
                now = TimeUnit.MILLISECONDS.toNanos(200)
                channel.advanceTimeBy(200, TimeUnit.MILLISECONDS)
                channel.runScheduledPendingTasks()
            }
            assertEquals(listOf("payload", "eof", "read-complete"), events)
            assertFalse(ProxyTransportActivity.hasPending(channel))
        } finally { channel.finishAndReleaseAll(); scope.cancel() }
    }
}
