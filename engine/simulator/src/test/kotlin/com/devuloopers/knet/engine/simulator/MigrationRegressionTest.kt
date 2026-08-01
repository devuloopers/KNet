package com.devuloopers.knet.engine.simulator

import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val manager = NetworkSimulatorManager()
        manager.applyPreset(NetworkProfiles.MOBILE_3G)
        assertNotNull(manager.activeProfile)
        manager.reset()
    }
}
