package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.WebChannelStreamFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertTrue

class WebChannelStreamFormatterTest {
    private val formatter = WebChannelStreamFormatter()

    @Test
    fun testWebChannelMatching() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/x-grpc-webchannel"), ""))
    }
}
