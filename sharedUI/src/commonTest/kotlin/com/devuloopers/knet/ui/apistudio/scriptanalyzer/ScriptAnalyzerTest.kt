package com.devuloopers.knet.ui.apistudio.scriptanalyzer

import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.DiagnosticSeverity
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptExecutionPhase
import com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test suite for [ScriptAnalyzer] and [PreRequestRule].
 * Verifies real-time editor diagnostic generation, text range calculation, and Quick Fix creation.
 */
class ScriptAnalyzerTest {

    private val analyzer = ScriptAnalyzer()

    /**
     * Verifies that pm.response and pm.test in Pre-request script trigger a warning diagnostic and Quick Fix.
     */
    @Test
    fun testPreRequestRuleDiagnosticsAndQuickFix() {
        val script = """
            console.log("Starting pre-request");
            pm.test("Status code is 200", function () {
                pm.response.to.have.status(200);
            });
        """.trimIndent()

        val result = analyzer.analyze(script, ScriptExecutionPhase.PRE_REQUEST)

        assertEquals(1, result.diagnostics.size)
        val diag = result.diagnostics.first()
        assertEquals(DiagnosticSeverity.WARNING, diag.severity)
        assertTrue(diag.message.contains("Tests tab"))

        assertEquals(1, result.quickFixes.size)
        val quickFix = result.quickFixes.first()
        assertTrue(quickFix is ScriptQuickFix.MoveToTestsTab)
        assertEquals("Move to Tests Tab", quickFix.title)
    }

    /**
     * Verifies that post-response phase scripts do not trigger pre-request assertion warnings.
     */
    @Test
    fun testPostResponsePhaseNoDiagnostics() {
        val script = """
            pm.test("Status code is 200", function () {
                pm.response.to.have.status(200);
            });
        """.trimIndent()

        val result = analyzer.analyze(script, ScriptExecutionPhase.POST_RESPONSE)

        assertTrue(result.diagnostics.isEmpty())
        assertTrue(result.quickFixes.isEmpty())
    }
}
