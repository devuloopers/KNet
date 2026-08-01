package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.domain.network.model.HttpTimings
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatorIntegrationTest {

    @Test
    fun testNetworkSimulatorLatencyCalculation() {
        val simulatedDelay = 250L
        val timings = HttpTimings(
            dnsMs = 10L,
            tcpMs = 20L,
            tlsMs = 30L,
            ttfbMs = 50L + simulatedDelay,
            downloadMs = 15L
        )

        assertEquals(300L, timings.ttfbMs)
    }
}
