package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.ProtobufBinaryFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertTrue

class ProtobufBinaryFormatterTest {
    private val formatter = ProtobufBinaryFormatter()

    @Test
    fun testProtobufMatching() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/x-protobuf"), ""))
        assertTrue(formatter.matches(emptyMap(), "[Binary payload]"))
    }
}
