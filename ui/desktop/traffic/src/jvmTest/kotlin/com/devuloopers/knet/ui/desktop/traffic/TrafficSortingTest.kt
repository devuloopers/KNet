package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.model.TrafficSort
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficSortField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for TrafficSort in `:ui:desktop:traffic`.
 */
class TrafficSortingTest {

    @Test
    fun `TrafficSort defaults to TIME descending`() {
        val sort = TrafficSort()
        assertEquals(TrafficSortField.TIME, sort.field)
        assertFalse(sort.ascending)
    }
}
