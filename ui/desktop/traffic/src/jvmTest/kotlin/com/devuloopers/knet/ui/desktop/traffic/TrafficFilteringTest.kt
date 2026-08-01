package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.model.TrafficFilter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for TrafficFilter in `:ui:desktop:traffic`.
 */
class TrafficFilteringTest {

    @Test
    fun `TrafficFilter default properties match ALL`() {
        val filter = TrafficFilter()
        assertEquals("ALL", filter.method)
        assertEquals("ALL", filter.statusGroup)
        assertEquals("ALL", filter.protocol)
    }
}
