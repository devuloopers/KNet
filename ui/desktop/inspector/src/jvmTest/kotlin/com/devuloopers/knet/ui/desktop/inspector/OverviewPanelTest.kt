package com.devuloopers.knet.ui.desktop.inspector

import com.devuloopers.knet.ui.desktop.inspector.model.TransactionOverview
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for TransactionOverview in `:ui:desktop:inspector`.
 */
class OverviewPanelTest {

    @Test
    fun `TransactionOverview holds metadata correctly`() {
        val overview = TransactionOverview(
            id = "tx_123",
            url = "https://api.knet.dev/users",
            host = "api.knet.dev",
            statusCode = 200,
            totalDurationMs = 85
        )

        assertEquals("tx_123", overview.id)
        assertEquals("https://api.knet.dev/users", overview.url)
        assertEquals(200, overview.statusCode)
        assertEquals(85, overview.totalDurationMs)
    }
}
