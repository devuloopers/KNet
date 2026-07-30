package com.devuloopers.knet.scriptengine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test suite for [ResultCollector].
 * Verifies lock-free atomic result collection, log capture, and timing metrics.
 */
class ResultCollectorTest {

    /**
     * Tests adding test assertion results atomically.
     */
    @Test
    fun testAddTestResult() {
        val collector = ResultCollector()
        collector.addTestResult("Status Code 200", true, null, 15L)
        collector.addTestResult("Valid Header", false, "Header missing", 5L)

        val results = collector.getTestResults()
        assertEquals(2, results.size)
        assertTrue(results[0].passed)
        assertEquals("Status Code 200", results[0].name)
        assertEquals(15L, results[0].durationMs)

        assertEquals("Valid Header", results[1].name)
        assertEquals("Header missing", results[1].errorMessage)
    }

    /**
     * Tests logging messages atomically.
     */
    @Test
    fun testAddLog() {
        val collector = ResultCollector()
        collector.addLog("Log line 1")
        collector.addLog("[WARN] Log line 2")

        val logs = collector.getLogs()
        assertEquals(2, logs.size)
        assertEquals("Log line 1", logs[0])
        assertEquals("[WARN] Log line 2", logs[1])
    }
}
