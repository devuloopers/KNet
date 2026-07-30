package com.devuloopers.knet.scriptengine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test suite for [ExceptionFormatter].
 * Verifies standard exception and PolyglotException formatting into ScriptExecutionResult.Error objects.
 */
class ExceptionFormatterTest {

    /**
     * Verifies formatting of standard RuntimeException instances.
     */
    @Test
    fun testFormatStandardException() {
        val ex = RuntimeException("Null pointer error")
        val error = ExceptionFormatter.format(ex)

        assertEquals("Null pointer error", error.message)
    }

    /**
     * Verifies formatting of exceptions with null messages.
     */
    @Test
    fun testFormatNullMessageException() {
        val ex = IllegalArgumentException()
        val error = ExceptionFormatter.format(ex)

        assertTrue(error.message.contains("IllegalArgumentException"))
    }
}
