package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.GrpcWebBodyFormatter
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertTrue

class GrpcWebBodyFormatterTest {
    private val formatter = GrpcWebBodyFormatter()

    @Test
    fun testGrpcWebMatching() {
        assertTrue(formatter.matches(mapOf("content-type" to "application/grpc-web"), ""))
        assertTrue(formatter.matches(mapOf("content-type" to "application/grpc-web-text"), ""))
    }
}
