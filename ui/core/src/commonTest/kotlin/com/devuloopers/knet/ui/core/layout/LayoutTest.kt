package com.devuloopers.knet.ui.core.layout

import com.devuloopers.knet.ui.core.theme.KNetElevation
import com.devuloopers.knet.ui.core.theme.KNetShapes
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit tests verifying layout primitive tokens in `:ui:core`.
 */
class LayoutTest {

    @Test
    fun `layout shape and elevation tokens are valid`() {
        assertNotNull(KNetShapes.Medium)
        assertNotNull(KNetElevation.None)
        assertNotNull(KNetElevation.Medium)
    }
}
