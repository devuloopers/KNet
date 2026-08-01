package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.InspectorIntent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for Search intent in `:ui:desktop:inspector`.
 */
class SearchTest {

    @Test
    fun `InspectorIntent Search holds query`() {
        val intent = InspectorIntent.Search(query = "Authorization")
        assertEquals("Authorization", intent.query)
    }
}
