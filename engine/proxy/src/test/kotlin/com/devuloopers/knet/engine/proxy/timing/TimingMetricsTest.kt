package com.devuloopers.knet.engine.proxy.timing

import com.devuloopers.knet.traffic.model.ExchangeTimings
import org.junit.Assert.assertEquals
import org.junit.Test

class TimingMetricsTest {

    @Test
    fun canonicalExchangeTimingsPreserveObservedPhases() {
        val timings = ExchangeTimings(
            dnsMillis = 12L,
            connectMillis = 25L,
            tlsMillis = 45L,
            firstByteMillis = 80L,
            downloadMillis = 15L,
        )

        assertEquals(12L, timings.dnsMillis)
        assertEquals(25L, timings.connectMillis)
        assertEquals(45L, timings.tlsMillis)
        assertEquals(80L, timings.firstByteMillis)
        assertEquals(15L, timings.downloadMillis)
    }
}
