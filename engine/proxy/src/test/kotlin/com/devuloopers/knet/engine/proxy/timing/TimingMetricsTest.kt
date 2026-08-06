package com.devuloopers.knet.engine.proxy.timing

import com.devuloopers.knet.domain.clientNetwork.model.HttpTimings
import org.junit.Assert.assertEquals
import org.junit.Test

class TimingMetricsTest {

    @Test
    fun testHttpTimingsProperties() {
        val timings = HttpTimings(
            dnsMs = 12L,
            tcpMs = 25L,
            tlsMs = 45L,
            ttfbMs = 80L,
            downloadMs = 15L
        )

        assertEquals(12L, timings.dnsMs)
        assertEquals(25L, timings.tcpMs)
        assertEquals(45L, timings.tlsMs)
        assertEquals(80L, timings.ttfbMs)
        assertEquals(15L, timings.downloadMs)
    }
}
