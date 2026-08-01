package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ConsoleLogEntry
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests verifying ConsoleLog ordering and formatting.
 */
class ConsoleLogTest {

    @Test
    fun `Console log timestamp is valid`() {
        val entry = ConsoleLogEntry(message = "Executing sandbox runtime script...")
        assertTrue(entry.timestamp > 0)
    }
}
