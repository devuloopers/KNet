package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickControlPopupPositionProviderTest {
    private val provider = QuickControlPopupPositionProvider()

    @Test
    fun `popover aligns below the toolbar without overflowing either window edge`() {
        assertEquals(660, provider.calculatePosition(IntRect(800, 0, 980, 28), IntSize(1000, 600),
            LayoutDirection.Ltr, IntSize(320, 240)).x)
        assertEquals(32, provider.calculatePosition(IntRect(800, 0, 980, 28), IntSize(1000, 600),
            LayoutDirection.Ltr, IntSize(320, 240)).y)
        assertEquals(0, provider.calculatePosition(IntRect(0, 0, 100, 28), IntSize(1000, 600),
            LayoutDirection.Ltr, IntSize(320, 240)).x)
    }

    @Test
    fun `popover flips above a low anchor and remains bounded in rtl and oversized cases`() {
        assertEquals(256, provider.calculatePosition(IntRect(800, 500, 980, 528), IntSize(1000, 600),
            LayoutDirection.Ltr, IntSize(320, 240)).y)
        assertEquals(680, provider.calculatePosition(IntRect(800, 0, 980, 28), IntSize(1000, 600),
            LayoutDirection.Rtl, IntSize(320, 240)).x)
        assertEquals(0, provider.calculatePosition(IntRect(0, 0, 20, 20), IntSize(120, 80),
            LayoutDirection.Ltr, IntSize(320, 240)).y)
    }
}
