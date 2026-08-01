package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.table.TrafficColumn
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for TrafficColumn definitions in `:ui:desktop:traffic`.
 */
class TrafficTableTest {

    @Test
    fun `TrafficColumn enum contains status and method columns`() {
        assertEquals("Status", TrafficColumn.STATUS.label)
        assertEquals("Method", TrafficColumn.METHOD.label)
    }
}
