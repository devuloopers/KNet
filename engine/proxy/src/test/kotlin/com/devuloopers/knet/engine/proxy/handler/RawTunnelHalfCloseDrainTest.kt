package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.KNetProxyRuntimePolicy
import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.traffic.model.*
import io.netty.channel.embedded.EmbeddedChannel
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Abandoned half-closed tunnels release admission without timing out healthy or draining sockets. */
class RawTunnelHalfCloseDrainTest {
    @Test fun `healthy quiet tunnel has no drain deadline`(): Unit = Fixture().use { fixture ->
        fixture.advance(60_001)
        fixture.assertOpen()
        assertEquals(0L, KNetProxyRuntimePolicy().rawTunnelIdleTimeoutMillis)
        assertEquals(60_000L, KNetProxyRuntimePolicy().rawTunnelHalfCloseIdleTimeoutMillis)
        assertFailsWith<IllegalArgumentException> { KNetProxyRuntimePolicy(rawTunnelHalfCloseIdleTimeoutMillis = 0) }
    }

    @Test fun `either unilateral EOF releases both sockets after idle grace with an explicit cause`() {
        TrafficDirection.entries.forEach { direction -> Fixture().use { fixture ->
            fixture.owner.observeInputShutdown(direction)
            fixture.advance(99); fixture.assertOpen()
            fixture.advance(2)
            assertFalse(fixture.client.isActive || fixture.server.isActive)
            assertEquals(TrafficTerminationReason.Transport.TUNNEL_HALF_CLOSE_IDLE_TIMEOUT,
                assertIs<ExchangeTerminalOutcome.Cancelled>(fixture.outcomes.single()).reason)
            assertEquals(if (direction == TrafficDirection.CLIENT_TO_SERVER) OpaqueFlowCloseInitiator.DEVICE
                else OpaqueFlowCloseInitiator.SERVER, fixture.closure?.initiator)
            assertFalse(fixture.owner.writeStarted(), "Terminal cleanup rejects late delivery")
        } }
    }

    @Test fun `reverse progress repeatedly resets grace past original deadline`(): Unit = Fixture().use { fixture ->
        fixture.owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER)
        repeat(5) {
            fixture.advance(90); fixture.assertOpen()
            assertTrue(fixture.owner.writeStarted())
            fixture.owner.writeFinished(true)
        }
        fixture.owner.observeInputShutdown(TrafficDirection.SERVER_TO_CLIENT)
        fixture.advance(1000)
        assertEquals(listOf<ExchangeTerminalOutcome>(ExchangeTerminalOutcome.Completed), fixture.outcomes)
    }

    @Test fun `queued shaped write suspends grace and receives a fresh interval on delivery`(): Unit = Fixture().use { fixture ->
        fixture.owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER)
        fixture.advance(90)
        assertTrue(fixture.owner.writeStarted())
        fixture.advance(1000); fixture.assertOpen()
        fixture.owner.writeFinished(true)
        fixture.advance(99); fixture.assertOpen()
        fixture.advance(2)
        assertIs<ExchangeTerminalOutcome.Cancelled>(fixture.outcomes.single())
    }

    @Test fun `duplicate EOF cannot pretend the opposite direction finished`(): Unit = Fixture().use { fixture ->
        repeat(5) { fixture.owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER) }
        fixture.assertOpen()
        fixture.advance(101)
        assertIs<ExchangeTerminalOutcome.Cancelled>(fixture.outcomes.single())
    }

    @Test fun `write failure and explicit cancellation cancel old timer without replacing outcome`() {
        listOf(false, true).forEach { failedWrite -> Fixture().use { fixture ->
            fixture.owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER)
            val expected = if (failedWrite) ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.DUPLEX_WRITE_FAILED)
                else ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Lifecycle.PROXY_STOPPED)
            if (failedWrite) { fixture.owner.writeStarted(); fixture.owner.writeFinished(false) }
            else fixture.owner.terminate(expected)
            fixture.advance(1000)
            assertEquals(listOf(expected), fixture.outcomes)
        } }
    }

    private class Fixture : AutoCloseable {
        val outcomes = mutableListOf<ExchangeTerminalOutcome>()
        var closure: OpaqueFlowClosure? = null
        val client = EmbeddedChannel().apply { freezeTime() }
        val server = EmbeddedChannel().apply { freezeTime() }
        private val capture = object : ProxyOpaqueFlowCapture {
            override fun observeBytes(direction: TrafficDirection, byteCount: Int, occurredAtEpochMillis: Long) = Unit
            override fun observeClosure(closure: OpaqueFlowClosure) { this@Fixture.closure = closure }
            override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) { outcomes += outcome }
        }
        val owner = RawTunnelLifecycle(capture, 100).apply { attach(client, server) }
        fun advance(millis: Long) { listOf(client, server).forEach { it.advanceTimeBy(millis, TimeUnit.MILLISECONDS); it.runScheduledPendingTasks() } }
        fun assertOpen() { assertTrue(client.isActive && server.isActive); assertTrue(outcomes.isEmpty()) }
        override fun close() { owner.terminate(ExchangeTerminalOutcome.Completed); client.finishAndReleaseAll(); server.finishAndReleaseAll() }
    }
}
