package com.devuloopers.knet.engine.script.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultCollectorTest {

    @Test
    fun testCollectorAccumulation() {
        val collector = ResultCollector()
        collector.addTestResult("Test 1", true, null, 50L)
        collector.addTestResult("Test 2", false, "Failed", 100L)
        collector.addLog("Log 1")

        val results = collector.getTestResults()
        val logs = collector.getLogs()

        assertEquals(2, results.size)
        assertTrue(results[0].passed)
        assertEquals("Failed", results[1].errorMessage)

        assertEquals(1, logs.size)
        assertEquals("Log 1", logs[0])
    }
}
