package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.export.CurlGenerator
import com.devuloopers.knet.engine.session.export.HTTPArchiveExporter
import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val tx = TestFixtures.createHttpTransaction()
        val har = HTTPArchiveExporter.export(listOf(tx))
        val curl = CurlGenerator.generate(tx)

        assertNotNull(har)
        assertNotNull(curl)
    }
}
