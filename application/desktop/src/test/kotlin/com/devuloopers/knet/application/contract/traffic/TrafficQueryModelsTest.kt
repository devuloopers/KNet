package com.devuloopers.knet.application.contract.traffic

import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrafficQueryModelsTest {

    @Test
    fun `arrival boundaries and counts reject negative values while an empty baseline is valid`() {
        assertEquals(TrafficArrivalCheckpoint(0, 0), TrafficArrivalCheckpoint())
        assertEquals(0L, TrafficArrivals(TrafficArrivalCheckpoint(), 0).count)
        assertFailsWith<IllegalArgumentException> { TrafficArrivalCheckpoint(exchanges = -1) }
        assertFailsWith<IllegalArgumentException> { TrafficArrivalCheckpoint(opaqueFlows = -1) }
        assertFailsWith<IllegalArgumentException> { TrafficArrivals(TrafficArrivalCheckpoint(), -1) }
    }

    @Test
    fun `read points order equal timestamps by namespaced identity and validate paging anchors`() {
        val http = TrafficReadPoint(TrafficRecordId.Exchange(com.devuloopers.knet.traffic.id.ExchangeId("same")), 1_000, TrafficCaptureSequence(9))
        val opaque = TrafficReadPoint(TrafficRecordId.Opaque(com.devuloopers.knet.traffic.id.OpaqueFlowId("same")), 1_000, TrafficCaptureSequence(1))
        kotlin.test.assertTrue(http < opaque)
        kotlin.test.assertTrue(http.isAfter(TrafficArrivalCheckpoint(8, 10)))
        kotlin.test.assertFalse(opaque.isAfter(TrafficArrivalCheckpoint(8, 10)))
        assertFailsWith<IllegalArgumentException> { http.copy(startedAtEpochMillis = -1) }
        assertFailsWith<IllegalArgumentException> { TrafficPageQuery(limit = 1, newerThan = http) }
        assertFailsWith<IllegalArgumentException> { TrafficPageQuery(limit = 1, olderThan = http, direction = TrafficSortDirection.OLDEST_FIRST) }
        assertFailsWith<IllegalArgumentException> { TrafficPageQuery(limit = 1, newerThan = http, olderThan = http) }
        assertFailsWith<IllegalArgumentException> { TrafficPageQuery(limit = 1, newerThan = http, cursor = TrafficPageCursor("test"), direction = TrafficSortDirection.OLDEST_FIRST) }
    }

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
