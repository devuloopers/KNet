package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ScriptDiagnostic
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptDiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying static analyzer ScriptDiagnostic representations.
 */
class DiagnosticsTest {

    @Test
    fun `ScriptDiagnostic fields match constructor parameters`() {
        val diag = ScriptDiagnostic(line = 12, message = "Unused variable", severity = ScriptDiagnosticSeverity.WARNING)
        assertEquals(12, diag.line)
        assertEquals("Unused variable", diag.message)
        assertEquals(ScriptDiagnosticSeverity.WARNING, diag.severity)
    }
}
