package com.devuloopers.knet.companion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompanionFlowMetadataTest {
    @Test
    fun `codec round trips a verified Android application identity`() {
        val metadata = CompanionFlowMetadata("com.example.streaming")

        assertEquals(metadata, CompanionFlowMetadataCodec.decode(CompanionFlowMetadataCodec.encode(metadata)))
    }

    @Test
    fun `codec rejects unsupported malformed and oversized values`() {
        assertNull(CompanionFlowMetadataCodec.decode("v2.Y29tLmV4YW1wbGU"))
        assertNull(CompanionFlowMetadataCodec.decode("v1.%%%"))
        assertNull(CompanionFlowMetadataCodec.decode("v1." + "A".repeat(1_025)))
    }
}
