package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    @Test
    fun testHighVolumeFormattingPerformance() {
        val duration = measureTimeMillis {
            repeat(1000) {
                BodyFormatterRegistry.resolveFormat(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_JSON)
            }
        }
        assertTrue(duration < 1000, "1000 format resolutions must take less than 1 second")
    }
}
