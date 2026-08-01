package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.export.HTTPArchiveExporter
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceRegressionTest {

    @Test
    fun testHeaderSerializationScalability() {
        val headers = (1..50).map { "Header-$it" to "Value-$it" }

        val duration = measureTimeMillis {
            repeat(1000) {
                HttpTransactionMapper.serializeHeaders(headers)
            }
        }

        assertTrue(duration < 1000, "1000 header serializations must complete in under 1 second")
    }

    @Test
    fun testHarExporterPerformance() {
        val transactions = (1..100).map { i ->
            TestFixtures.createHttpTransaction(id = "tx-$i")
        }

        val duration = measureTimeMillis {
            HTTPArchiveExporter.export(transactions)
        }

        assertTrue(duration < 1000, "Exporting 100 transactions to HAR 1.2 must take under 1 second")
    }
}
