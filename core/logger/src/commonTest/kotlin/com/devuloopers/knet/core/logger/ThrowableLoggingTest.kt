package com.devuloopers.knet.core.logger

import kotlin.test.Test
import kotlin.test.assertTrue

class ThrowableLoggingTest {

    @Test
    fun testErrorLoggingWithThrowable() {
        val exception = IllegalStateException("Test exception cause")
        KNetLogger.error(tag = LogTags.HTTP, throwable = exception) {
            "An error occurred during request dispatch"
        }
        assertTrue(true)
    }

    @Test
    fun testErrorLoggingWithNullThrowable() {
        KNetLogger.error(tag = LogTags.PROXY, throwable = null) {
            "Proxy error without root cause"
        }
        assertTrue(true)
    }
}
