package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.export.HarLog
import com.devuloopers.knet.engine.session.export.HarLogRoot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HTTPArchiveModelTest {

    @Test
    fun testHarModelSerializationRoundTrip() {
        val root = HarLogRoot(HarLog(entries = emptyList()))
        val jsonStr = Json.encodeToString(root)
        val decoded = Json.decodeFromString<HarLogRoot>(jsonStr)

        assertEquals("1.2", decoded.log.version)
        assertEquals("KNet Proxy", decoded.log.creator.name)
    }
}
