package com.devuloopers.knet.engine.proxy.pipeline

import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.engine.proxy.handler.KNetRawTunnelRelayHandler
import com.devuloopers.knet.engine.proxy.handler.RawTunnelLifecycle
import com.devuloopers.knet.traffic.model.*
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.timeout.WriteTimeoutHandler
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Quiet established tunnels are independent of HTTP idle deadlines; explicit tunnel deadlines stay observable. */
class RawTunnelInactivityTest {
    @Test fun `default raw policy cancels the HTTP idle timer but retains stalled-write protection`() {
        val fixture = Fixture()
        try {
            configureRawTunnelInactivity(fixture.channel.pipeline(), KNetProxyRuntimePolicy().rawTunnelIdleTimeoutMillis)
            Thread.sleep(180)
            fixture.channel.runScheduledPendingTasks()
            assertTrue(fixture.channel.isActive)
            assertTrue(fixture.peer.isActive)
            assertTrue(fixture.outcomes.isEmpty())
            assertNotNull(fixture.channel.pipeline().get(WriteTimeoutHandler::class.java))
        } finally { fixture.close() }
    }

    @Test fun `an explicit raw inactivity deadline remains a typed timeout`() {
        val fixture = Fixture()
        try {
            configureRawTunnelInactivity(fixture.channel.pipeline(), 100)
            Thread.sleep(180)
            fixture.channel.runScheduledPendingTasks()
            assertFalse(fixture.channel.isActive)
            assertEquals(TrafficTerminationReason.Transport.READ_TIMED_OUT,
                assertIs<ExchangeTerminalOutcome.Failed>(fixture.outcomes.single()).reason)
        } finally { fixture.close() }
    }

    @Test fun `raw policy permits zero and rejects negative limits`() {
        assertEquals(0L, KNetProxyRuntimePolicy().rawTunnelIdleTimeoutMillis)
        assertFailsWith<IllegalArgumentException> { KNetProxyRuntimePolicy(rawTunnelIdleTimeoutMillis = -1) }
    }

    private class Fixture {
        val outcomes = mutableListOf<ExchangeTerminalOutcome>()
        private val capture = object : ProxyOpaqueFlowCapture {
            override fun observeBytes(direction: TrafficDirection, byteCount: Int, occurredAtEpochMillis: Long) = Unit
            override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) { outcomes += outcome }
        }
        val peer = EmbeddedChannel()
        val channel = EmbeddedChannel().apply {
            pipeline().addLast(PipelineHandlerNames.READ_TIMEOUT, ProxyTransportIdleHandler(100, TimeUnit.MILLISECONDS))
            pipeline().addLast(PipelineHandlerNames.WRITE_TIMEOUT, WriteTimeoutHandler(100, TimeUnit.MILLISECONDS))
            pipeline().addLast(PipelineHandlerNames.DUPLEX_RELAY, KNetRawTunnelRelayHandler(peer,
                TrafficDirection.CLIENT_TO_SERVER, capture, RawTunnelLifecycle(capture)))
        }
        fun close() { channel.finishAndReleaseAll(); peer.finishAndReleaseAll() }
    }
}
