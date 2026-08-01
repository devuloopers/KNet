package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class FormatterConcurrencyTest {

    @Test
    fun testConcurrentFormatting() {
        val executor = Executors.newFixedThreadPool(10)

        repeat(100) {
            executor.submit {
                BodyFormatterRegistry.resolveFormat(mapOf("content-type" to "application/json"), TestFixtures.SAMPLE_JSON)
                BodyFormatterRegistry.prettyPrintBody(mapOf("content-type" to "application/xml"), TestFixtures.SAMPLE_XML)
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertTrue(finished, "Concurrent payload formatting must finish cleanly")
    }
}
