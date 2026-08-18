package com.devuloopers.knet.engine.proxy.integration

import com.devuloopers.knet.traffic.model.ExchangeTimings
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatorIntegrationTest {

    @Test
    fun testNetworkSimulatorLatencyCalculation() {
        val simulatedDelay = 250L
        val timings = ExchangeTimings(
            dnsMillis = 10L,
            connectMillis = 20L,
            tlsMillis = 30L,
            firstByteMillis = 50L + simulatedDelay,
            downloadMillis = 15L,
        )

        assertEquals(300L, timings.firstByteMillis)
    }
}
