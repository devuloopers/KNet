package com.devuloopers.knet.ui.core.badge

import com.devuloopers.knet.ui.core.theme.KNetColors
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit tests verifying badge color mapping constants in `:ui:core`.
 */
class BadgeTest {

    @Test
    fun `badge colors are configured`() {
        assertNotNull(KNetColors.SuccessGreen)
        assertNotNull(KNetColors.ErrorRed)
        assertNotNull(KNetColors.WarningOrange)
        assertNotNull(KNetColors.PurpleWS)
        assertNotNull(KNetColors.ActiveBlue)
    }
}
