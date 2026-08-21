package com.devuloopers.knet.ui.desktop.traffic.table

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficAutoScrollPolicyTest {
    @Test
    fun `loading older pages does not request a jump to the top`() {
        assertFalse(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 625L,
                wasEnabled = true,
                isEnabled = true,
            ),
        )
        assertFalse(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 425L,
                wasEnabled = true,
                isEnabled = true,
            ),
        )
    }

    @Test
    fun `new capture or explicit enablement requests the newest row`() {
        assertTrue(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 626L,
                wasEnabled = true,
                isEnabled = true,
            ),
        )
        assertTrue(
            shouldAutoScrollToNewest(
                previousSequence = 625L,
                currentSequence = 625L,
                wasEnabled = false,
                isEnabled = true,
            ),
        )
    }
}
