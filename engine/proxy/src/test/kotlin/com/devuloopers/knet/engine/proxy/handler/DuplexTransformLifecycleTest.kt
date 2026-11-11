package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.engine.proxy.pipeline.ProxyTransportActivity
import com.devuloopers.knet.engine.proxy.pipeline.ProxyTransportIdleHandler
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Paused protocol operations retain their owner only while active, and late completions cannot emit payloads. */
class DuplexTransformLifecycleTest {
    @Test fun `an active delayed transform survives inactivity and releases its transport hold on delivery`() {
        Fixture().use { fixture ->
            fixture.channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1)))
            assertTrue(ProxyTransportActivity.hasPending(fixture.channel))
            Thread.sleep(150)
            fixture.channel.runScheduledPendingTasks()
            assertTrue(fixture.channel.isActive)
            fixture.result.complete(ProxyDuplexTransformResult.Forward(byteArrayOf(2)))
            fixture.channel.runPendingTasks()
            assertFalse(ProxyTransportActivity.hasPending(fixture.channel))
            val output = fixture.peer.readOutbound<ByteBuf>()
            assertNotNull(output)
            try { assertEquals(2, output.readByte().toInt()) } finally { output.release() }
        }
    }

    @Test fun `closing a paused transform releases its hold and discards an uncooperative late completion`() {
        Fixture().use { fixture ->
            fixture.channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1)))
            fixture.channel.close()
            assertFalse(ProxyTransportActivity.hasPending(fixture.channel))
            assertEquals(1, fixture.cancelled)
            fixture.result.complete(ProxyDuplexTransformResult.Forward(byteArrayOf(2)))
            fixture.channel.runPendingTasks()
            assertNull(fixture.peer.readOutbound<Any>())
            assertEquals(1, fixture.terminated)
        }
    }

    private class Fixture : AutoCloseable {
        val result = CompletableFuture<ProxyDuplexTransformResult>()
        val peer = EmbeddedChannel()
        var cancelled = 0
        var terminated = 0
        private val transformer = object : ProxyDuplexTransformer {
            override fun transform(direction: TrafficDirection, payload: ByteArray, occurredAtEpochMillis: Long) = result
            override fun cancel(reason: TrafficTerminationReason?) { cancelled++ }
        }
        val channel = EmbeddedChannel(ProxyTransportIdleHandler(100, TimeUnit.MILLISECONDS), KNetDuplexRelayHandler(
            peer, TrafficDirection.CLIENT_TO_SERVER, emptyList(), transformer,
            DuplexRelayLifecycle(emptyList(), transformer) { _, _ -> terminated++ },
        ))
        override fun close() {
            channel.finishAndReleaseAll()
            peer.finishAndReleaseAll()
        }
    }
}
