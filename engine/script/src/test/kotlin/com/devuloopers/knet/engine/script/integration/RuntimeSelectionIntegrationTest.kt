package com.devuloopers.knet.engine.script.integration

import com.devuloopers.knet.engine.script.internal.RuntimeCapabilityDetector
import kotlin.test.Test
import kotlin.test.assertNotNull

class RuntimeSelectionIntegrationTest {

    @Test
    fun testRuntimeSelectionCapabilityDetector() {
        val detector = RuntimeCapabilityDetector()
        val selectedRuntime = detector.selectRuntime()

        assertNotNull(selectedRuntime)
    }
}
