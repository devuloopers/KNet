package com.devuloopers.knet.ui.desktop.inspector

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for Viewers in `:ui:desktop:inspector`.
 */
class ViewerTest {

    @Test
    fun `Hex dump formatting function generates valid string`() {
        val raw = "ABC"
        val dump = raw.toByteArray().joinToString(" ") { "%02X".format(it) }
        assertTrue(dump.contains("41 42 43"))
    }
}
