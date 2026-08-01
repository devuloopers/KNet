package com.devuloopers.knet.engine.simulator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkProfileTest {

    @Test
    fun testNetworkProfileValidation() {
        val valid = NetworkProfile("Custom", 100_000, 100, 10)
        assertEquals("Custom", valid.name)
        assertTrue(valid.isActive)

        assertFailsWith<IllegalArgumentException> {
            NetworkProfile(packetLossPercent = 101)
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkProfile(latencyMs = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkProfile(bandwidthBytesPerSecond = 0)
        }
    }

    @Test
    fun testIsActiveProperty() {
        assertFalse(NetworkProfiles.NONE.isActive)
        assertTrue(NetworkProfiles.LOSSY.isActive)
        assertTrue(NetworkProfiles.OFFLINE.isActive)
    }
}
