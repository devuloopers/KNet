package com.devuloopers.knet.engine.simulator

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertFalse

class SimulatorLifecycleTest {

    @Test
    fun testChannelClosedDuringScheduledLatency() {
        val manager = NetworkSimulatorManager()
        manager.applyProfile(NetworkProfile(latencyMs = 5000))

        val handler = KNetNetworkSimulatorHandler(manager)
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest()
        channel.writeInbound(req)
        channel.close()

        assertFalse(channel.isOpen)
    }
}
