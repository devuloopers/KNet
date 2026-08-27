package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrafficQueryModelsTest {

    @Test
    fun `body chunk owns a defensive copy`() {
        val source = byteArrayOf(1, 2, 3)
        val chunk = BodyChunk(source, offset = 0L, endOfBody = true)
        source[0] = 9

        val firstRead = chunk.copyBytes()
        firstRead[1] = 8

        assertContentEquals(byteArrayOf(1, 2, 3), chunk.copyBytes())
        assertEquals(3, chunk.size)
    }

    @Test
    fun `traffic page query enforces a bounded page size`() {
        assertFailsWith<IllegalArgumentException> {
            TrafficPageQuery(
                sessionId = CaptureSessionId("session-1"),
                limit = 10_000,
            )
        }
    }

    @Test
    fun `traffic facet counts reject negative aggregates`() {
        assertFailsWith<IllegalArgumentException> {
            TrafficFacetCounts(totalCount = -1L)
        }
    }
}
