package com.devuloopers.knet.engine.session

import com.devuloopers.knet.engine.session.model.SessionStatistics
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStatisticsTest {

    @Test
    fun testSessionStatisticsCounters() {
        val stats = SessionStatistics()
        stats.incrementRequests()
        stats.incrementResponses()
        stats.addBytesCaptured(512)
        stats.addBytesStored(256)

        assertEquals(1, stats.totalRequests)
        assertEquals(1, stats.totalResponses)
        assertEquals(512, stats.bytesCaptured)
        assertEquals(256, stats.bytesStored)

        stats.reset()
        assertEquals(0, stats.totalRequests)
        assertEquals(0, stats.totalResponses)
    }
}
