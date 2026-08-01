package com.devuloopers.knet.engine.simulator

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkSimulatorManagerTest {

    @Test
    fun testApplyProfileAndReset() {
        val manager = NetworkSimulatorManager()
        assertEquals(NetworkProfiles.NONE, manager.activeProfile)

        manager.applyPreset(NetworkProfiles.MOBILE_3G)
        assertEquals(NetworkProfiles.MOBILE_3G, manager.activeProfile)

        manager.reset()
        assertEquals(NetworkProfiles.NONE, manager.activeProfile)
    }
}
