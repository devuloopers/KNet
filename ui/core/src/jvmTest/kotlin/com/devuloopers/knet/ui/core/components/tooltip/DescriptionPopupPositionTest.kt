package com.devuloopers.knet.ui.core.components.tooltip

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** Guards edge placement so contextual help stays readable without covering the hovered menu row. */
class DescriptionPopupPositionTest {
    @Test fun `ordinary controls prefer below and move above near the bottom`() {
        val provider = DescriptionPopupPositionProvider(4)
        val window = IntSize(800, 600)
        val popup = IntSize(360, 150)
        assertEquals(IntOffset(100, 140), provider.calculatePosition(IntRect(100, 100, 380, 136), window, LayoutDirection.Ltr, popup))
        assertEquals(IntOffset(440, 376), provider.calculatePosition(IntRect(520, 530, 800, 566), window, LayoutDirection.Ltr, popup))
        assertEquals(IntOffset(20, 140), provider.calculatePosition(IntRect(100, 100, 380, 136), window, LayoutDirection.Rtl, popup))
    }

    @Test fun `menu descriptions prefer available side and clamp oversized narrow windows`() {
        val provider = DescriptionPopupPositionProvider(4, KNetTooltipPlacement.Beside)
        val popup = IntSize(360, 150)
        val window = IntSize(1000, 600)
        assertEquals(IntOffset(284, 100), provider.calculatePosition(IntRect(0, 100, 280, 134), window, LayoutDirection.Ltr, popup))
        assertEquals(IntOffset(356, 100), provider.calculatePosition(IntRect(720, 100, 1000, 134), window, LayoutDirection.Ltr, popup))
        assertEquals(IntOffset(356, 100), provider.calculatePosition(IntRect(720, 100, 1000, 134), window, LayoutDirection.Rtl, popup))
        assertEquals(IntOffset.Zero, provider.calculatePosition(IntRect(0, 0, 320, 34), IntSize(320, 100), LayoutDirection.Ltr, popup))
    }

    @Test fun `narrow help moves below the entire menu instead of covering another option`() {
        val provider = DescriptionPopupPositionProvider(4, KNetTooltipPlacement.Beside, IntRect(12, 52, 272, 162))
        assertEquals(IntOffset(0, 166), provider.calculatePosition(IntRect(12, 56, 272, 90),
            IntSize(360, 560), LayoutDirection.Ltr, IntSize(360, 150)))
    }
}
