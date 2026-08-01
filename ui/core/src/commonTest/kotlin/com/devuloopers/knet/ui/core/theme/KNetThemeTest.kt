package com.devuloopers.knet.ui.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests verifying design system tokens in `:ui:core`.
 */
class KNetThemeTest {

    @Test
    fun `KNetColors background and surface tokens are defined`() {
        assertNotNull(KNetColors.BackgroundDark)
        assertNotNull(KNetColors.SurfaceDark)
        assertNotNull(KNetColors.BorderDark)
        assertNotNull(KNetColors.ActiveBlue)
    }

    @Test
    fun `KNetTypography styles are configured`() {
        assertNotNull(KNetTypography.Title)
        assertNotNull(KNetTypography.Subtitle)
        assertNotNull(KNetTypography.Body)
        assertNotNull(KNetTypography.MonospaceCode)
    }

    @Test
    fun `KNetSpacing tokens are positive Dp values`() {
        assertEquals(2.0f, KNetSpacing.Micro.value)
        assertEquals(4.0f, KNetSpacing.Tiny.value)
        assertEquals(8.0f, KNetSpacing.Small.value)
        assertEquals(12.0f, KNetSpacing.Medium.value)
        assertEquals(16.0f, KNetSpacing.Large.value)
        assertEquals(24.0f, KNetSpacing.ExtraLarge.value)
    }

    @Test
    fun `KNetDimensions tokens are configured`() {
        assertEquals(28.0f, KNetDimensions.InputFieldHeight.value)
        assertEquals(36.0f, KNetDimensions.HeaderHeight.value)
        assertEquals(24.0f, KNetDimensions.StatusBarHeight.value)
    }
}
