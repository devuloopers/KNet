package com.devuloopers.knet.engine.simulator

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SimulatorIntegrationTest {

    @Test
    fun testEmbeddedChannelPipelineWithBandwidthThrottling() {
        val manager = NetworkSimulatorManager()
        manager.applyPreset(NetworkProfiles.MOBILE_3G)

        val handler = KNetNetworkSimulatorHandler(manager)
        val channel = EmbeddedChannel(handler)

        val shaper = channel.pipeline().get("knet.trafficShaper")
        assertNotNull(shaper, "ChannelTrafficShapingHandler must be dynamically installed when bandwidth limit is set")
    }
}
