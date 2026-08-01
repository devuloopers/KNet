package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ConsoleLogEntry
import com.devuloopers.knet.ui.desktop.scripting.model.ConsoleLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying Console filters and attributes.
 */
class ConsoleTest {

    @Test
    fun `Console log entry attributes match input`() {
        val log = ConsoleLogEntry(level = ConsoleLogLevel.WARN, message = "Low memory warning")
        assertEquals(ConsoleLogLevel.WARN, log.level)
        assertEquals("Low memory warning", log.message)
    }
}
