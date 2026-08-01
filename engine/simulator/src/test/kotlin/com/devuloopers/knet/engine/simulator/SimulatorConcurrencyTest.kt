package com.devuloopers.knet.engine.simulator

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class SimulatorConcurrencyTest {

    @Test
    fun testConcurrentProfileHotSwapping() {
        val manager = NetworkSimulatorManager()
        val executor = Executors.newFixedThreadPool(10)

        val profiles = listOf(
            NetworkProfiles.NONE,
            NetworkProfiles.MOBILE_2G,
            NetworkProfiles.MOBILE_3G,
            NetworkProfiles.MOBILE_4G,
            NetworkProfiles.MOBILE_5G,
            NetworkProfiles.LOSSY
        )

        repeat(100) { i ->
            executor.submit {
                manager.applyProfile(profiles[i % profiles.size])
                manager.activeProfile
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertTrue(finished, "Concurrent profile hot-swapping must finish within timeout")
    }
}
