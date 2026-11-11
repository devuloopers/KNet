package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import com.devuloopers.knet.traffic.model.TrafficTerminationReason
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.ReferenceCountUtil
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** A transport deadline must retain its typed cause instead of becoming an indistinguishable I/O failure. */
class RawTunnelFailureReasonTest {
    @Test fun `read inactivity retains its timeout reason`() = checkFailure(
        ReadTimeoutException.INSTANCE, TrafficTerminationReason.Transport.READ_TIMED_OUT)

    @Test fun `stalled writes retain their timeout reason`() = checkFailure(
        WriteTimeoutException.INSTANCE, TrafficTerminationReason.Transport.WRITE_TIMED_OUT)

    @Test fun `ordinary IO faults remain failures`() = checkFailure(
        IOException("fixture I/O failure"), TrafficTerminationReason.Transport.DUPLEX_IO_FAILED)

    @Test fun `an actual stalled write keeps its timeout despite close and failed promise callbacks`() {
        val outcomes = mutableListOf<ExchangeTerminalOutcome>()
        val capture = object : ProxyOpaqueFlowCapture {
            override fun observeBytes(direction: TrafficDirection, byteCount: Int, occurredAtEpochMillis: Long) = Unit
            override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) { outcomes += outcome }
        }
        val lifecycle = RawTunnelLifecycle(capture)
        val source = EmbeddedChannel()
        val stalled = StalledWrite()
        val peer = EmbeddedChannel(stalled, WriteTimeoutHandler(100, TimeUnit.MILLISECONDS),
            KNetRawTunnelRelayHandler(source, TrafficDirection.SERVER_TO_CLIENT, capture, lifecycle))
        source.pipeline().addLast(KNetRawTunnelRelayHandler(peer, TrafficDirection.CLIENT_TO_SERVER, capture, lifecycle))
        val payload = Unpooled.buffer().writeByte(42)
        try {
            source.writeInbound(payload)
            assertTrue(outcomes.isEmpty())
            Thread.sleep(180)
            peer.runScheduledPendingTasks()
            source.runPendingTasks()
            assertEquals(TrafficTerminationReason.Transport.WRITE_TIMED_OUT,
                assertIs<ExchangeTerminalOutcome.Failed>(outcomes.single()).reason)
            assertFalse(source.isActive)
            assertFalse(peer.isActive)
            assertEquals(0, payload.refCnt())
        } finally { source.finishAndReleaseAll(); peer.finishAndReleaseAll() }
    }

    private fun checkFailure(cause: Throwable, expected: TrafficTerminationReason) {
        val outcomes = mutableListOf<ExchangeTerminalOutcome>()
        val capture = object : ProxyOpaqueFlowCapture {
            override fun observeBytes(direction: TrafficDirection, byteCount: Int, occurredAtEpochMillis: Long) = Unit
            override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) { outcomes += outcome }
        }
        val peer = EmbeddedChannel()
        val channel = EmbeddedChannel(KNetRawTunnelRelayHandler(peer, TrafficDirection.CLIENT_TO_SERVER,
            capture, RawTunnelLifecycle(capture)))
        try {
            channel.pipeline().fireExceptionCaught(cause)
            assertEquals(expected, assertIs<ExchangeTerminalOutcome.Failed>(outcomes.single()).reason)
            assertFalse(channel.isActive)
            assertFalse(peer.isActive)
        } finally { channel.finishAndReleaseAll(); peer.finishAndReleaseAll() }
    }

    /** Models a socket that accepts ownership but never makes a pending transport write complete. */
    private class StalledWrite : ChannelOutboundHandlerAdapter() {
        private var pending: Pair<Any, ChannelPromise>? = null
        override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
            check(pending == null)
            pending = message to promise
        }
        override fun close(context: ChannelHandlerContext, promise: ChannelPromise) {
            val write = pending
            pending = null
            write?.let { (message, completion) ->
                ReferenceCountUtil.release(message)
                completion.tryFailure(ClosedChannelException())
            }
            super.close(context, promise)
        }
    }
}
