package com.devuloopers.knet.engine.simulator

import io.netty.channel.embedded.EmbeddedChannel
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    @Test
    fun testPassthroughPerformanceNegligibleOverhead() {
        val manager = NetworkSimulatorManager()
        val handler = KNetNetworkSimulatorHandler(manager)
        val channel = EmbeddedChannel(handler)

        val duration = measureTimeMillis {
            repeat(1000) {
                val req = TestFixtures.createFullHttpRequest()
                channel.writeInbound(req)
                channel.readInbound<Any>()
            }
        }

        assertTrue(duration < 1000, "1000 passthrough channel reads must complete within 1 second")
    }
}
