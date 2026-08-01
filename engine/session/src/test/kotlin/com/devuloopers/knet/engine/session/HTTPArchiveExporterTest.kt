package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.export.HTTPArchiveExporter
import kotlin.test.Test
import kotlin.test.assertTrue

class HTTPArchiveExporterTest {

    @Test
    fun testExportToHarJson() {
        val tx = TestFixtures.createHttpTransaction()
        val harJson = HTTPArchiveExporter.export(listOf(tx))

        assertTrue(harJson.contains("1.2"), "HAR JSON should contain version 1.2")
        assertTrue(harJson.contains("KNet Proxy"), "HAR JSON should contain creator name")
        assertTrue(harJson.contains("https://api.example.com/v1/users"), "HAR JSON should contain request URL")
        assertTrue(harJson.contains("200"), "HAR JSON should contain response status code")
    }
}
