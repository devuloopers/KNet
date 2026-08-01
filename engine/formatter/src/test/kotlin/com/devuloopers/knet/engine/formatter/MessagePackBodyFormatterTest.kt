package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.MessagePackBodyFormatter
import kotlin.test.Test
import kotlin.test.assertTrue

class MessagePackBodyFormatterTest {
    private val formatter = MessagePackBodyFormatter()

    @Test
    fun testMessagePackMatching() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/msgpack"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "application/x-msgpack"), ""))
    }
}
