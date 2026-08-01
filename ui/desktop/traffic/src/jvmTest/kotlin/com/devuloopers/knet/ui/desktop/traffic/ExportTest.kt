package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.model.TrafficMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Export & Metrics in `:ui:desktop:traffic`.
 */
class ExportTest {

    @Test
    fun `TrafficMetrics default counters`() {
        val metrics = TrafficMetrics()
        assertEquals(0, metrics.totalRequests)
    }
}
