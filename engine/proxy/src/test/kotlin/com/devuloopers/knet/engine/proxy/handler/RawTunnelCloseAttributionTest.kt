package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.traffic.model.*
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** First observed closure is retained before propagation; absence of evidence never invents a peer. */
class RawTunnelCloseAttributionTest {
    @Test fun `device EOF remains the owner when KNet subsequently closes the server socket`() = peerEof(true)
    @Test fun `server EOF remains the owner when KNet subsequently closes the device socket`() = peerEof(false)

    private fun peerEof(device: Boolean) {
        Fixture().use { fixture ->
            val source = if (device) fixture.downstream else fixture.upstream
            source.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
            val first = fixture.capture.closure
            assertEquals(if (device) OpaqueFlowCloseInitiator.DEVICE else OpaqueFlowCloseInitiator.SERVER, first?.initiator)
            assertEquals(OpaqueFlowCloseReason.PEER_EOF, first?.reason)
            // This explicit close is the propagated cleanup, not a new initiator.
            (if (device) fixture.upstream else fixture.downstream).close()
            assertEquals(first, fixture.capture.closure)
            assertEquals(1, fixture.capture.outcomes.size)
        }
    }

    @Test fun `local close is recorded before close future listeners can cancel capture`() {
        Fixture().use { fixture ->
            var observedByCloseListener: OpaqueFlowClosure? = null
            fixture.downstream.closeFuture().addListener { observedByCloseListener = fixture.capture.closure }
            fixture.downstream.close()
            assertEquals(OpaqueFlowCloseInitiator.KNET, observedByCloseListener?.initiator)
            assertEquals(OpaqueFlowCloseReason.LOCAL_CLOSE, observedByCloseListener?.reason)
        }
    }

    @Test fun `timeouts identify KNet but arbitrary IO faults remain unknown`() {
        listOf(ReadTimeoutException.INSTANCE to OpaqueFlowCloseReason.INACTIVITY_TIMEOUT,
            WriteTimeoutException.INSTANCE to OpaqueFlowCloseReason.WRITE_TIMEOUT,
            IOException("Connection reset") to OpaqueFlowCloseReason.IO_ERROR).forEach { (exception, reason) ->
            Fixture().use { fixture ->
                fixture.upstream.pipeline().fireExceptionCaught(exception)
                assertEquals(reason, fixture.capture.closure?.reason)
                assertEquals(if (exception is IOException) OpaqueFlowCloseInitiator.UNKNOWN else OpaqueFlowCloseInitiator.KNET,
                    fixture.capture.closure?.initiator)
                assertIs<ExchangeTerminalOutcome.Failed>(fixture.capture.outcomes.single())
            }
        }
    }

    @Test fun `opposite EOFs within one clock tick remain ambiguous and later callbacks cannot replace evidence`() {
        val capture = Capture()
        val owner = RawTunnelLifecycle(capture)
        owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER, 10)
        owner.observeInputShutdown(TrafficDirection.SERVER_TO_CLIENT, 10)
        assertEquals(OpaqueFlowClosure(OpaqueFlowCloseInitiator.UNKNOWN, OpaqueFlowCloseReason.CONCURRENT_PEER_EOF, 10), capture.closure)
        owner.observeClosure(OpaqueFlowCloseInitiator.KNET, OpaqueFlowCloseReason.LOCAL_CLOSE, 11)
        owner.terminate(ExchangeTerminalOutcome.Completed)
        assertEquals(OpaqueFlowCloseInitiator.UNKNOWN, capture.closure?.initiator)
        assertEquals(1, capture.outcomes.size)
    }

    @Test fun `inactive without an observed close signal remains unknown`() {
        Fixture().use { fixture ->
            fixture.downstream.unsafe().close(fixture.downstream.newPromise())
            assertEquals(OpaqueFlowCloseInitiator.UNKNOWN, fixture.capture.closure?.initiator)
            assertEquals(OpaqueFlowCloseReason.UNKNOWN, fixture.capture.closure?.reason)
        }
    }

    @Test fun `concurrent opposite EOF callbacks publish one terminal snapshot with ambiguous provenance`() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(50) {
                val capture = Capture()
                val owner = RawTunnelLifecycle(capture)
                val gate = CountDownLatch(1)
                val results = TrafficDirection.entries.map { direction -> workers.submit {
                    check(gate.await(2, TimeUnit.SECONDS))
                    owner.observeInputShutdown(direction, 10)
                } }
                gate.countDown()
                results.forEach { result -> result.get(2, TimeUnit.SECONDS) }
                assertEquals(OpaqueFlowCloseInitiator.UNKNOWN, capture.closure?.initiator)
                assertEquals(OpaqueFlowCloseReason.CONCURRENT_PEER_EOF, capture.closure?.reason)
                assertEquals(listOf<ExchangeTerminalOutcome>(ExchangeTerminalOutcome.Completed), capture.outcomes)
            }
        } finally { workers.shutdownNow() }
    }

    private class Capture : ProxyOpaqueFlowCapture {
        var closure: OpaqueFlowClosure? = null
        val outcomes = mutableListOf<ExchangeTerminalOutcome>()
        override fun observeClosure(closure: OpaqueFlowClosure) { this.closure = closure }
        override fun observeBytes(direction: TrafficDirection, byteCount: Int, occurredAtEpochMillis: Long) = Unit
        override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) { outcomes += outcome }
    }

    private class Fixture : AutoCloseable {
        val capture = Capture()
        private val owner = RawTunnelLifecycle(capture)
        val downstream = EmbeddedChannel()
        val upstream = EmbeddedChannel()
        init {
            downstream.pipeline().addLast(KNetRawTunnelRelayHandler(upstream, TrafficDirection.CLIENT_TO_SERVER, capture, owner))
            upstream.pipeline().addLast(KNetRawTunnelRelayHandler(downstream, TrafficDirection.SERVER_TO_CLIENT, capture, owner))
        }
        override fun close() { downstream.finishAndReleaseAll(); upstream.finishAndReleaseAll() }
    }
}
