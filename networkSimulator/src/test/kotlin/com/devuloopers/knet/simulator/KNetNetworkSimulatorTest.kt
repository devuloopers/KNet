package com.devuloopers.knet.simulator

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit and integration tests for the network simulator features.
 * Validates profile registration, managers configuration, and event simulator behaviors.
 */
class KNetNetworkSimulatorTest {

    @Test
    fun testNetworkProfileValidation() {
        // Valid profiles should construct without issue
        val valid = NetworkProfile(bandwidthBytesPerSecond = 1000L, latencyMs = 100L, packetLossPercent = 10)
        assertEquals(1000L, valid.bandwidthBytesPerSecond)
        assertEquals(100L, valid.latencyMs)
        assertEquals(10, valid.packetLossPercent)

        // Invalid packet loss threshold
        assertFailsWith<IllegalArgumentException> {
            NetworkProfile(packetLossPercent = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkProfile(packetLossPercent = 101)
        }

        // Invalid latency value
        assertFailsWith<IllegalArgumentException> {
            NetworkProfile(latencyMs = -10L)
        }

        // Invalid bandwidth value
        assertFailsWith<IllegalArgumentException> {
            NetworkProfile(bandwidthBytesPerSecond = -50L)
        }
    }

    @Test
    fun testDefaultNoneProfile() {
        val none = NetworkProfile.NONE
        assertFalse(none.isActive)
        assertEquals(null, none.bandwidthBytesPerSecond)
        assertEquals(0L, none.latencyMs)
        assertEquals(0, none.packetLossPercent)
    }

    @Test
    fun testProfilePresets() {
        val gprs = NetworkProfile.MOBILE_2G
        assertTrue(gprs.isActive)
        assertEquals(40_000L, gprs.bandwidthBytesPerSecond)
        assertEquals(500L, gprs.latencyMs)

        val mobile3g = NetworkProfile.MOBILE_3G
        assertTrue(mobile3g.isActive)
        assertEquals(50_000L, mobile3g.bandwidthBytesPerSecond)
        assertEquals(300L, mobile3g.latencyMs)

        val mobile4g = NetworkProfile.MOBILE_4G
        assertTrue(mobile4g.isActive)
        assertEquals(5_000_000L, mobile4g.bandwidthBytesPerSecond)
        assertEquals(50L, mobile4g.latencyMs)

        val lossy = NetworkProfile.LOSSY
        assertTrue(lossy.isActive)
        assertEquals(200L, lossy.latencyMs)
        assertEquals(20, lossy.packetLossPercent)

        val offline = NetworkProfile.OFFLINE
        assertTrue(offline.isActive)
        assertEquals(100, offline.packetLossPercent)
    }

    @Test
    fun testManagerHotSwapping() {
        val manager = NetworkSimulatorManager()
        assertEquals(NetworkProfile.NONE, manager.activeProfile)

        manager.applyPreset(NetworkProfile.MOBILE_3G)
        assertEquals(NetworkProfile.MOBILE_3G, manager.activeProfile)

        manager.reset()
        assertEquals(NetworkProfile.NONE, manager.activeProfile)
    }
}
