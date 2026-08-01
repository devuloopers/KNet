package com.devuloopers.knet.engine.simulator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NetworkProfilesTest {

    @Test
    fun testBuiltInPresetsInvariants() {
        assertEquals("Passthrough", NetworkProfiles.NONE.name)
        assertEquals("Offline", NetworkProfiles.OFFLINE.name)
        assertEquals(100, NetworkProfiles.OFFLINE.packetLossPercent)

        assertEquals("2G GPRS", NetworkProfiles.MOBILE_2G.name)
        assertEquals(40_000, NetworkProfiles.MOBILE_2G.bandwidthBytesPerSecond)
        assertEquals(500, NetworkProfiles.MOBILE_2G.latencyMs)

        assertEquals("3G UMTS", NetworkProfiles.MOBILE_3G.name)
        assertEquals("4G LTE", NetworkProfiles.MOBILE_4G.name)
        assertEquals("5G NR", NetworkProfiles.MOBILE_5G.name)
        assertEquals("Wi-Fi", NetworkProfiles.WIFI.name)
        assertEquals("Satellite", NetworkProfiles.SATELLITE.name)
        assertEquals("Lossy Network", NetworkProfiles.LOSSY.name)
    }
}
