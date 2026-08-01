package com.devuloopers.knet.engine.script.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptSecurityTest {

    @Test
    fun testScriptSecurityValidation() {
        val safeScript = "console.log('Hello'); pm.test('ok', function() {});"
        val safeValidation = ScriptSecurity.validate(safeScript)
        assertTrue(safeValidation.isValid)

        val unsafeScript = "System.exit(0);"
        val unsafeValidation = ScriptSecurity.validate(unsafeScript)
        assertFalse(unsafeValidation.isValid)
        assertTrue(unsafeValidation.errorMessage!!.contains("System.exit"))
    }
}
