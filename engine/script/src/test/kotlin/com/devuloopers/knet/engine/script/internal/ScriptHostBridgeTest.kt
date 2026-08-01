package com.devuloopers.knet.engine.script.internal

import com.devuloopers.knet.engine.script.api.EnvironmentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptHostBridgeTest {

    @Test
    fun testHostBridgeInteractions() {
        val collector = ResultCollector()
        val env = EnvironmentStore()
        val bridge = ScriptHostBridge(collector, env)

        bridge.addTest("Bridge Test", true, null)
        bridge.log("Bridge Log")
        bridge.setEnv("bridgeKey", "bridgeVal")

        assertEquals("bridgeVal", bridge.getEnv("bridgeKey"))
        assertEquals(1, collector.getTestResults().size)
        assertTrue(collector.getTestResults()[0].passed)
        assertEquals(1, collector.getLogs().size)
    }
}
