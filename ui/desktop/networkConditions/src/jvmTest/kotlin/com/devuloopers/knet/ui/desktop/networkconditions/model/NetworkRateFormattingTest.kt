package com.devuloopers.knet.ui.desktop.networkconditions.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Shared bit-rate units retain useful precision for both measured readings and configured profiles. */
class NetworkRateFormattingTest {
    @Test
    fun `network units preserve fractional and sub kilobit measurements without locale rounding loss`() {
        listOf(null to "Unlimited", 0L to "0 kbps", 128L to "128 bps", 1_000L to "1 kbps",
            12_500L to "12.5 kbps", 128_000L to "128 kbps", 1_600_000L to "1.6 Mbps",
            1_250_000_000L to "1.3 Gbps", Long.MAX_VALUE to "9223372036.9 Gbps").forEach { (value, label) ->
            assertEquals(label, formatNetworkRate(value))
        }
    }
}
