package com.devuloopers.knet.engine.script.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionFormatterTest {

    @Test
    fun testFormatThrowable() {
        val ex = RuntimeException("Custom runtime error")
        val errorResult = ExceptionFormatter.format(ex)

        assertEquals("Custom runtime error", errorResult.message)
    }
}
