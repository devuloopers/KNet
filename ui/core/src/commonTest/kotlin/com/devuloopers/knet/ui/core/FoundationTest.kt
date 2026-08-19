package com.devuloopers.knet.ui.core

import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.color.KNetDarkColors
import com.devuloopers.knet.ui.core.foundation.color.KNetLightColors
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions
import com.devuloopers.knet.ui.core.foundation.elevation.KNetElevation
import com.devuloopers.knet.ui.core.foundation.motion.KNetMotion
import com.devuloopers.knet.ui.core.foundation.responsive.WindowSizeClass
import com.devuloopers.knet.ui.core.foundation.responsive.calculateWindowInfo
import com.devuloopers.knet.ui.core.foundation.shapes.KNetShapes
import com.devuloopers.knet.ui.core.foundation.spacing.KNetSpacing
import com.devuloopers.knet.ui.core.foundation.typography.KNetTypography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies KNet Design System v3 foundation tokens and responsive matrix calculations.
 */
class FoundationTest {

    @Test
    fun testColorPalettes() {
        assertNotNull(KNetDarkColors.background)
        assertNotNull(KNetDarkColors.accent)
        assertNotNull(KNetDarkColors.semantic.success)
        assertNotNull(KNetDarkColors.semantic.error)

        assertNotNull(KNetLightColors.background)
        assertNotNull(KNetLightColors.accent)
        assertNotNull(KNetLightColors.semantic.success)
        assertNotNull(KNetLightColors.semantic.error)
    }

    @Test
    fun testTypographyTokens() {
        assertNotNull(KNetTypography.display)
        assertNotNull(KNetTypography.heading)
        assertNotNull(KNetTypography.titleLarge)
        assertNotNull(KNetTypography.titleMedium)
        assertNotNull(KNetTypography.bodyMedium)
        assertNotNull(KNetTypography.caption)
        assertNotNull(KNetTypography.codeMedium)
        assertNotNull(KNetTypography.codeSmall)
    }

    @Test
    fun testSpacingAndDimensionsTokens() {
        assertEquals(2.dp, KNetSpacing.xxs)
        assertEquals(4.dp, KNetSpacing.xs)
        assertEquals(8.dp, KNetSpacing.sm)
        assertEquals(12.dp, KNetSpacing.md)
        assertEquals(16.dp, KNetSpacing.lg)
        assertEquals(20.dp, KNetSpacing.xl)
        assertEquals(24.dp, KNetSpacing.xxl)
        assertEquals(32.dp, KNetSpacing.xxxl)
        assertEquals(40.dp, KNetSpacing.huge)
        assertEquals(48.dp, KNetSpacing.massive)
        assertEquals(64.dp, KNetSpacing.giant)

        assertEquals(32.dp, KNetDimensions.toolbarHeight)
        assertEquals(24.dp, KNetDimensions.statusBarHeight)
        assertEquals(48.dp, KNetDimensions.navigationWidth)
        assertEquals(240.dp, KNetDimensions.sidebarWidth)
        assertEquals(26.dp, KNetDimensions.tableRowHeight)
        assertEquals(520.dp, KNetDimensions.dialogWidthMedium)
    }

    @Test
    fun testShapesElevationMotion() {
        assertNotNull(KNetShapes.small)
        assertNotNull(KNetShapes.medium)
        assertEquals(0.dp, KNetElevation.level0)
        assertEquals(2.dp, KNetElevation.level1)
        assertEquals(4.dp, KNetElevation.level2)
        assertEquals(8.dp, KNetElevation.level3)
        assertNotNull(KNetMotion.easingStandard)
        assertTrue(KNetMotion.animationsEnabled)
        assertTrue(KNetMotion.durationFeedback > KNetMotion.durationSlow)
    }

    @Test
    fun testResponsiveWindowInfoCalculation() {
        val compactInfo = calculateWindowInfo(1024.dp, 600.dp)
        assertEquals(WindowSizeClass.Compact, compactInfo.widthSizeClass)
        assertEquals(WindowSizeClass.Compact, compactInfo.heightSizeClass)

        val mediumInfo = calculateWindowInfo(1600.dp, 900.dp)
        assertEquals(WindowSizeClass.Medium, mediumInfo.widthSizeClass)
        assertEquals(WindowSizeClass.Medium, mediumInfo.heightSizeClass)

        val expandedInfo = calculateWindowInfo(2560.dp, 1440.dp)
        assertEquals(WindowSizeClass.Expanded, expandedInfo.widthSizeClass)
        assertEquals(WindowSizeClass.Expanded, expandedInfo.heightSizeClass)
    }
}
