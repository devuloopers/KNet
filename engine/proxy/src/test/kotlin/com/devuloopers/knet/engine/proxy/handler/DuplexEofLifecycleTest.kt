package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformer
import com.devuloopers.knet.engine.proxy.inspection.ProxyDuplexTransformResult
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.handler.ssl.SslCloseCompletionEvent
import io.netty.util.ReferenceCountUtil
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.test.*

/** EOF must drain accepted bytes/decisions, release both sockets, and retain delivery failures. */
class DuplexEofLifecycleTest {
    @Test fun `both input EOFs release sockets before an idle timeout`() = Fixture().use { fixture ->
        fixture.endBoth()
        fixture.assertCompleted()
    }

    @Test fun `duplicate TCP and SSL EOF for one side preserves the reverse direction`() = Fixture().use { fixture ->
        fixture.client.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
        fixture.client.pipeline().fireUserEventTriggered(SslCloseCompletionEvent.SUCCESS)
        assertTrue(fixture.client.isActive && fixture.server.isActive)
        fixture.server.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(8)))
        fixture.drain()
        val output = assertNotNull(fixture.client.readOutbound<ByteBuf>())
        try { assertEquals(8, output.readByte().toInt()) } finally { output.release() }
        fixture.server.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
        fixture.drain()
        fixture.assertCompleted()
    }

    @Test fun `EOF waits for a queued final write`() = pendingWrite(failed = false)
    @Test fun `failed final write wins over both clean EOFs`() = pendingWrite(failed = true)

    @Test fun `both EOFs wait for an asynchronous final transform and its delivery`() {
        val decision = CompletableFuture<ProxyDuplexTransformResult>()
        val transformer = object : ProxyDuplexTransformer {
            override fun transform(direction: TrafficDirection, payload: ByteArray, occurredAtEpochMillis: Long) = decision
            override fun cancel(reason: TrafficTerminationReason?) = Unit
        }
        Fixture(transformer).use { fixture ->
            fixture.client.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1)))
            fixture.endBoth()
            assertTrue(fixture.outcomes.isEmpty())
            decision.complete(ProxyDuplexTransformResult.Forward(byteArrayOf(9)))
            fixture.drain()
            val output = assertNotNull(fixture.server.readOutbound<ByteBuf>())
            try { assertEquals(9, output.readByte().toInt()) } finally { output.release() }
            fixture.assertCompleted()
        }
    }

    @Test fun `a transform failure at EOF remains a failure`() {
        val decision = CompletableFuture<ProxyDuplexTransformResult>()
        val transformer = object : ProxyDuplexTransformer {
            override fun transform(direction: TrafficDirection, payload: ByteArray, occurredAtEpochMillis: Long) = decision
            override fun cancel(reason: TrafficTerminationReason?) = Unit
        }
        Fixture(transformer).use { fixture ->
            fixture.client.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1)))
            fixture.endBoth()
            decision.completeExceptionally(IOException("deliberate transform failure"))
            fixture.drain()
            assertEquals(listOf<ExchangeTerminalOutcome>(ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Interception.DUPLEX_TRANSFORM_FAILED)), fixture.outcomes)
            assertFalse(fixture.client.isActive || fixture.server.isActive)
        }
    }

    private fun pendingWrite(failed: Boolean) = Fixture().use { fixture ->
        var pending: ChannelPromise? = null
        var payload: Any? = null
        fixture.server.pipeline().addFirst(object : ChannelOutboundHandlerAdapter() {
            override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
                payload = message
                pending = promise
            }
        })
        fixture.client.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(1)))
        fixture.endBoth()
        assertTrue(fixture.outcomes.isEmpty())
        assertTrue(fixture.client.isActive && fixture.server.isActive)
        ReferenceCountUtil.release(payload)
        if (failed) pending!!.setFailure(IOException("deliberate delivery failure")) else pending!!.setSuccess()
        fixture.drain()
        if (failed) {
            assertEquals(listOf<ExchangeTerminalOutcome>(ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED)), fixture.outcomes)
            assertFalse(fixture.client.isActive || fixture.server.isActive)
        } else fixture.assertCompleted()
    }

    private class Fixture(transformer: ProxyDuplexTransformer? = null) : AutoCloseable {
        val outcomes = mutableListOf<ExchangeTerminalOutcome>()
        val client = EmbeddedChannel()
        val server = EmbeddedChannel()
        private val lifecycle = DuplexRelayLifecycle(emptyList(), transformer) { outcome, _ -> outcomes += outcome }
        init {
            client.pipeline().addLast(KNetDuplexRelayHandler(server, TrafficDirection.CLIENT_TO_SERVER, emptyList(), transformer, lifecycle))
            server.pipeline().addLast(KNetDuplexRelayHandler(client, TrafficDirection.SERVER_TO_CLIENT, emptyList(), transformer, lifecycle))
        }
        fun endBoth() {
            client.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
            server.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
            drain()
        }
        fun drain() { repeat(5) { client.runPendingTasks(); server.runPendingTasks() } }
        fun assertCompleted() {
            assertFalse(client.isActive || server.isActive, "Both sockets must be released")
            assertEquals(listOf<ExchangeTerminalOutcome>(ExchangeTerminalOutcome.Completed), outcomes)
        }
        override fun close() { client.finishAndReleaseAll(); server.finishAndReleaseAll() }
    }
}
