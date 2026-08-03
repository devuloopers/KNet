package com.devuloopers.knet.ui.core

import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying KNet Design System v2.0 composite layout dimensions and bounds.
 */
class LayoutTest {

    @Test
    fun testLayoutDimensions() {
        assertEquals(32.dp, KNetDimensions.toolbarHeight)
        assertEquals(24.dp, KNetDimensions.statusBarHeight)
        assertEquals(48.dp, KNetDimensions.navigationWidth)
        assertEquals(240.dp, KNetDimensions.sidebarWidth)
    }
}
