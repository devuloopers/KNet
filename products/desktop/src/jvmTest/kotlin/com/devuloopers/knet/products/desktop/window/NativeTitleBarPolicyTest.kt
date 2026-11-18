package com.devuloopers.knet.products.desktop.window

import androidx.compose.ui.geometry.Rect
import kotlin.test.*

/** Native actions stay outside measured controls on supported macOS and Windows runtimes. */
class NativeTitleBarPolicyTest {
    @Test fun `unsupported runtimes and other operating systems retain native decorations`() {
        assertTrue(supportsNativeDeviceTitleBar("Mac OS X", true))
        assertFalse(supportsNativeDeviceTitleBar("Mac OS X", false))
        assertFalse(supportsNativeDeviceTitleBar("Linux", true))
        assertTrue(supportsNativeDeviceTitleBar("Windows 11", true))
        assertFalse(supportsNativeDeviceTitleBar("Windows 11", false))
    }
    @Test fun `fullscreen releases native inset without reserving space on fallback runtimes`() {
        assertEquals(84f, nativeControlInset(true, false, 0f, 84f))
        assertEquals(100f, nativeControlInset(true, false, 100f, 84f))
        assertEquals(0f, nativeControlInset(true, true, 100f, 84f))
        assertEquals(0f, nativeControlInset(false, false, 100f, 84f))
    }
    @Test fun `windows reserves its right controls and fallback preserves full content width`() {
        assertEquals(NativeTitleBarInsets(0f, 138f), nativeTitleBarInsets(true, true, false, 0f, 0f, 84f, 138f))
        assertEquals(NativeTitleBarInsets(0f, 180f), nativeTitleBarInsets(true, true, false, 0f, 180f, 84f, 138f))
        assertEquals(NativeTitleBarInsets(0f, 140f), nativeTitleBarInsets(true, true, true, 0f, 140f, 84f, 138f))
        assertEquals(NativeTitleBarInsets(0f, 0f), nativeTitleBarInsets(true, false, false, 0f, 180f, 84f, 138f))
        assertEquals(NativeTitleBarInsets(100f, 0f), nativeTitleBarInsets(false, true, false, 100f, 0f, 84f, 138f))
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
