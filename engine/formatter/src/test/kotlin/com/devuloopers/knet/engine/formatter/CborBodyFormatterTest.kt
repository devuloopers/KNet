package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.CborBodyFormatter
import kotlin.test.Test
import kotlin.test.assertTrue

class CborBodyFormatterTest {
    private val formatter = CborBodyFormatter()

    @Test
    fun testCborMatching() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/cbor"), ""))
    }
}
