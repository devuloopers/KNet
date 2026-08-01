package com.devuloopers.knet.engine.simulator

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkSimulationStatsTest {

    @Test
    fun testStatsCountersAndReset() {
        val stats = NetworkSimulationStats()
        stats.incrementPacketsDelayed()
        stats.incrementPacketsDropped()
        stats.addBytesDelayed(1024)
        stats.addBytesThrottled(2048)

        assertEquals(1, stats.packetsDelayed)
        assertEquals(1, stats.packetsDropped)
        assertEquals(1024, stats.bytesDelayed)
        assertEquals(2048, stats.bytesThrottled)

        stats.reset()
        assertEquals(0, stats.packetsDelayed)
        assertEquals(0, stats.packetsDropped)
        assertEquals(0, stats.bytesDelayed)
        assertEquals(0, stats.bytesThrottled)
    }
}
