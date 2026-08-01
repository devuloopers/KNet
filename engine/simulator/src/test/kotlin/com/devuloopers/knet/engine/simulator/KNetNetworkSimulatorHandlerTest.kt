package com.devuloopers.knet.engine.simulator

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KNetNetworkSimulatorHandlerTest {

    @Test
    fun testPacketLossDropsMessage() {
        val manager = NetworkSimulatorManager()
        manager.applyPreset(NetworkProfiles.OFFLINE) // 100% loss

        val stats = NetworkSimulationStats()
        val handler = KNetNetworkSimulatorHandler(manager, stats)
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest()
        channel.writeInbound(req)

        val read = channel.readInbound<Any>()
        assertNull(read, "100% packet loss must drop inbound message silently")
        assertEquals(1, stats.packetsDropped)
    }

    @Test
    fun testPassthroughForwardsMessage() {
        val manager = NetworkSimulatorManager()
        val stats = NetworkSimulationStats()
        val handler = KNetNetworkSimulatorHandler(manager, stats)
        val channel = EmbeddedChannel(handler)

        val req = TestFixtures.createFullHttpRequest("hello")
        channel.writeInbound(req)

        val read = channel.readInbound<io.netty.handler.codec.http.FullHttpRequest>()
        assertEquals("hello", read.content().toString(Charsets.UTF_8))
        assertEquals(0, stats.packetsDropped)
    }
}
