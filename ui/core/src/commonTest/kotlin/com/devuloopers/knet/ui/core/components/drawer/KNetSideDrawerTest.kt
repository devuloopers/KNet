package com.devuloopers.knet.ui.core.components.drawer

import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.dimensions.Dimensions
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies deterministic responsive width resolution for the shared drawer shell. */
class KNetSideDrawerTest {
    /** Standard and expanded drawers use their central design-system width tokens. */
    @Test
    fun `drawer size classes resolve design system widths`() {
        val dimensions = Dimensions()

        assertEquals(720.dp, resolveSideDrawerWidth(KNetSideDrawerSize.STANDARD, dimensions, 1_200.dp))
        assertEquals(880.dp, resolveSideDrawerWidth(KNetSideDrawerSize.EXPANDED, dimensions, 1_200.dp))
    }

    /** A drawer shrinks to its parent width instead of overflowing a compact workspace. */
    @Test
    fun `drawer width is bounded by available workspace width`() {
        assertEquals(
            480.dp,
            resolveSideDrawerWidth(KNetSideDrawerSize.EXPANDED, Dimensions(), 480.dp),
        )
    }
}
