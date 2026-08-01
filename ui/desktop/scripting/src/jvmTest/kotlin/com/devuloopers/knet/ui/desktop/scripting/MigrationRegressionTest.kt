package com.devuloopers.knet.ui.desktop.scripting

import com.devuloopers.knet.ui.desktop.scripting.model.ScriptPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MigrationRegressionTest ensures backwards compatibility with prior API contracts.
 */
class MigrationRegressionTest {

    @Test
    fun `ScriptPhase enum values are backwards compatible`() {
        assertEquals("PRE_REQUEST", ScriptPhase.PRE_REQUEST.name)
        assertEquals("TEST_ASSERTION", ScriptPhase.TEST_ASSERTION.name)
    }
}
