package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.TrafficScript
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying ScriptEditor model and properties.
 */
class ScriptEditorTest {

    @Test
    fun `TrafficScript editor initial code values`() {
        val script = TrafficScript(id = "1", name = "config.js", code = "log('hello')")
        assertEquals("config.js", script.name)
        assertEquals("log('hello')", script.code)
    }
}
