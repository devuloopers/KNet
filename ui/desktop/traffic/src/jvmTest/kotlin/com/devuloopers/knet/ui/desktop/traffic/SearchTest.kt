package com.devuloopers.knet.ui.desktop.traffic

import com.devuloopers.knet.ui.desktop.traffic.model.TrafficIntent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Search intent in `:ui:desktop:traffic`.
 */
class SearchTest {

    @Test
    fun `TrafficIntent Search holds query`() {
        val intent = TrafficIntent.Search("api.knet.dev")
        assertEquals("api.knet.dev", intent.query)
    }
}
