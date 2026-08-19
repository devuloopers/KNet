package com.devuloopers.knet.ui.desktop.traffic.model

import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InspectorPreparedCacheTest {
    @Test
    fun `evicts least recently used state when the byte budget is exceeded`() {
        val first = preparedState("first", "a".repeat(300))
        val second = preparedState("second", "b".repeat(300))
        val cache = InspectorPreparedCache(
            maximumEntries = 4,
            maximumRetainedBytes = first.estimatedRetainedBytes + second.estimatedRetainedBytes - 1L,
        )

        cache.put(first)
        cache.put(second)

        assertNull(cache["first"])
        assertNotNull(cache["second"])
    }

    @Test
    fun `does not retain a state larger than the complete cache budget`() {
        val cache = InspectorPreparedCache(maximumEntries = 4, maximumRetainedBytes = 1_024L)

        cache.put(preparedState("oversized", "payload"))

        assertNull(cache["oversized"])
    }

    private fun preparedState(id: String, body: String): InspectorPreparedState = InspectorPreparedState(
        transactionId = id,
        requestPayloadSpec = PayloadInspectionSpec(rawBody = body),
        loadState = InspectorLoadState.READY,
    )
}
