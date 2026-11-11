package com.devuloopers.knet.engine.proxy.handler

import com.devuloopers.knet.engine.proxy.capture.ProxyOpaqueFlowCapture
import com.devuloopers.knet.traffic.model.ExchangeTerminalOutcome
import com.devuloopers.knet.traffic.model.TrafficDirection
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Input EOF cannot make an opaque row complete while shaped/transport writes remain outstanding. */
class RawTunnelTerminalLifecycleTest {
    @Test fun `both EOF events wait for final write delivery before completing`() {
        val capture = Capture()
        val owner = RawTunnelLifecycle(capture)
        owner.writeStarted(); owner.writeStarted()
        owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER); owner.observeInputShutdown(TrafficDirection.SERVER_TO_CLIENT)
        assertTrue(capture.outcomes.isEmpty())
        owner.writeFinished(true)
        assertTrue(capture.outcomes.isEmpty())
        owner.writeFinished(true)
        assertEquals(listOf<ExchangeTerminalOutcome>(ExchangeTerminalOutcome.Completed), capture.outcomes.toList())
        owner.requestCompletion()
        assertEquals(1, capture.outcomes.size)
    }

    @Test fun `a late failed write cannot be hidden by already observed EOFs`() {
        val capture = Capture()
        val owner = RawTunnelLifecycle(capture)
        owner.writeStarted()
        owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER); owner.observeInputShutdown(TrafficDirection.SERVER_TO_CLIENT)
        owner.writeFinished(false)
        assertIs<ExchangeTerminalOutcome.Failed>(capture.outcomes.single())
    }

    @Test fun `concurrent direction completion produces one terminal row after every write`() {
        val workers = Executors.newFixedThreadPool(4)
        try {
            repeat(100) {
                val capture = Capture()
                val owner = RawTunnelLifecycle(capture)
                repeat(4) { owner.writeStarted() }
                owner.observeInputShutdown(TrafficDirection.CLIENT_TO_SERVER); owner.observeInputShutdown(TrafficDirection.SERVER_TO_CLIENT)
                val gate = CountDownLatch(1)
                val results = (0 until 4).map { workers.submit { gate.await(); owner.writeFinished(true) } }
                gate.countDown()
                results.forEach { it.get(3, TimeUnit.SECONDS) }
                assertEquals(listOf<ExchangeTerminalOutcome>(ExchangeTerminalOutcome.Completed), capture.outcomes.toList())
            }
        } finally { workers.shutdownNow() }
    }

    private class Capture : ProxyOpaqueFlowCapture {
        val outcomes = CopyOnWriteArrayList<ExchangeTerminalOutcome>()
        override fun observeBytes(direction: TrafficDirection, byteCount: Int, occurredAtEpochMillis: Long) = Unit
        override fun terminate(outcome: ExchangeTerminalOutcome, occurredAtEpochMillis: Long) { outcomes += outcome }
    }
}
