package com.devuloopers.knet.ui.desktop.httppanel

import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.timelineLabelColumnWidth
import kotlin.test.Test
import kotlin.test.assertEquals

/** Responsive sizing coverage for the aligned Timeline waterfall label column. */
class TimelineLayoutTest {
    @Test
    fun `normal inspector width preserves original label column`() {
        assertEquals(130.dp, timelineLabelColumnWidth(320.dp))
    }

    @Test
    fun `narrow inspector uses one bounded responsive label column`() {
        assertEquals(84.dp, timelineLabelColumnWidth(180.dp))
        assertEquals(60.dp, timelineLabelColumnWidth(120.dp))
    }
}
