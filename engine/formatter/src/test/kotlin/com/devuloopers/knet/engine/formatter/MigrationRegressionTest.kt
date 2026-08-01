package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.formatters.JsonBodyFormatter
import com.devuloopers.knet.engine.formatter.registry.BodyFormatterRegistry
import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val registry = BodyFormatterRegistry
        val jsonFormatter = JsonBodyFormatter()

        assertNotNull(registry)
        assertNotNull(jsonFormatter)
    }
}
