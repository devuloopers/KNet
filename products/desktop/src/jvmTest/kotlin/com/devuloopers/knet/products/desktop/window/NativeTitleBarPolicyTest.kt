package com.devuloopers.knet.products.desktop.window

import androidx.compose.ui.geometry.Rect
import kotlin.test.*

/** Native actions are enabled only outside actual measured controls and only on a supported macOS runtime. */
class NativeTitleBarPolicyTest {
    @Test fun `unsupported runtimes and other operating systems retain native decorations`() {
        assertTrue(supportsNativeDeviceTitleBar("Mac OS X", true))
        assertFalse(supportsNativeDeviceTitleBar("Mac OS X", false))
        assertFalse(supportsNativeDeviceTitleBar("Linux", true))
        assertFalse(supportsNativeDeviceTitleBar("Windows 11", true))
    }
    @Test fun `fullscreen releases native inset without reserving space on fallback runtimes`() {
        assertEquals(84f, nativeControlInset(true, false, 0f, 84f))
        assertEquals(100f, nativeControlInset(true, false, 100f, 84f))
        assertEquals(0f, nativeControlInset(true, true, 100f, 84f))
        assertEquals(0f, nativeControlInset(false, false, 100f, 84f))
    }
    @Test fun `native hit testing respects tab boundaries and releases removed regions`() {
        val regions = NativeTitleBarRegions()
        regions.update(listOf(Rect(84f, 0f, 240f, 40f), Rect(240f, 0f, 400f, 40f)))
        assertFalse(regions.isInteractive(50f, 20f))
        assertTrue(regions.isInteractive(100f, 20f))
        assertFalse(regions.isInteractive(401f, 20f))
        assertFalse(regions.isInteractive(100f, 41f))
        regions.update(emptyList())
        assertFalse(regions.isInteractive(100f, 20f))
    }
}
