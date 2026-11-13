package com.devuloopers.knet.products.desktop.window

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import java.awt.Toolkit
import java.awt.AWTEvent
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Uses the runtime's native macOS title-bar integration, retaining AppKit controls and window gestures.
 * Unsupported runtimes keep their complete native caption with the same device strip immediately below it.
 * No software imitation of close, zoom, minimize, drag or macOS double-click preferences is installed.
 */
@Composable
internal fun FrameWindowScope.MacDeviceTitleBar(content: @Composable (Dp, (List<Rect>) -> Unit) -> Unit) {
    val height = KNetTheme.dimensions.workspaceTabHeight
    val fallbackInset = KNetTheme.dimensions.nativeWindowControlsInset
    val density = LocalDensity.current
    val titleBar = remember(window) {
        if (supportsNativeDeviceTitleBar(System.getProperty("os.name"), JBR.isWindowDecorationsSupported())) {
            JBR.getWindowDecorations().createCustomTitleBar().apply { this.height = height.value }
        } else null
    }
    DisposableEffect(window, titleBar) {
        if (titleBar != null) JBR.getWindowDecorations().setCustomTitleBar(window, titleBar)
        onDispose { if (titleBar != null) JBR.getWindowDecorations().setCustomTitleBar(window, null as WindowDecorations.CustomTitleBar?) }
    }
    var fullscreen by remember(window) { mutableStateOf(false) }
    DisposableEffect(window, titleBar) {
        val registration = if (titleBar != null) observeMacFullscreen(window) { fullscreen = it } else null
        onDispose { registration?.close() }
    }
    val regions = remember(window) { NativeTitleBarRegions() }
    DisposableEffect(window, titleBar, density) {
        val listener = AWTEventListener { event ->
            if (titleBar != null && event is MouseEvent && event.id != MouseEvent.MOUSE_EXITED && event.id != MouseEvent.MOUSE_WHEEL &&
                event.source is java.awt.Component && SwingUtilities.getWindowAncestor(event.source as java.awt.Component) == window) {
                val point = SwingUtilities.convertPoint(event.source as java.awt.Component, event.point, window.contentPane)
                titleBar.forceHitTest(regions.isInteractive(point.x * density.density, point.y * density.density))
            }
        }
        if (titleBar != null) Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
        onDispose { if (titleBar != null) Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }
    SideEffect { titleBar?.height = height.value }
    val inset = nativeControlInset(titleBar != null, fullscreen, titleBar?.leftInset ?: 0f, fallbackInset.value).dp
    content(inset) { regions.update(it) }
}

/** Pure capability policy: native decoration support is required and Linux/Windows rollout remains separate. */
internal fun supportsNativeDeviceTitleBar(osName: String, runtimeSupported: Boolean): Boolean =
    runtimeSupported && osName.startsWith("Mac", ignoreCase = true)

/** Immutable bounded hit-test snapshot shared between Compose layout and AWT dispatch without a component listener leak. */
internal class NativeTitleBarRegions {
    @Volatile private var regions: List<Rect> = emptyList()
    fun update(bounds: List<Rect>) { regions = bounds.take(4096).toList() }
    fun isInteractive(x: Float, y: Float): Boolean = regions.any { it.contains(androidx.compose.ui.geometry.Offset(x, y)) }
}

/** Native fullscreen removes the ordinary traffic-light reservation; unsupported runtimes need no custom inset. */
internal fun nativeControlInset(supported: Boolean, fullscreen: Boolean, nativeInset: Float, fallback: Float): Float =
    if (!supported || fullscreen) 0f else maxOf(nativeInset, fallback)

// Public macOS AWT hooks are loaded reflectively so Linux/Windows do not need Apple-only classes.
// Registration is owned by the window composition and always removed on disposal.
private fun observeMacFullscreen(window: java.awt.Window, changed: (Boolean) -> Unit): AutoCloseable? = runCatching {
    val listenerType = Class.forName("com.apple.eawt.FullScreenListener")
    val utilities = Class.forName("com.apple.eawt.FullScreenUtilities")
    val listener = java.lang.reflect.Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, arguments ->
        when (method.name) {
            "windowEnteringFullScreen", "windowEnteredFullScreen" -> { changed(true); null }
            "windowExitingFullScreen", "windowExitedFullScreen" -> { changed(false); null }
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === arguments?.firstOrNull()
            "toString" -> "KNet native fullscreen listener"
            else -> null
        }
    }
    utilities.getMethod("addFullScreenListenerTo", java.awt.Window::class.java, listenerType).invoke(null, window, listener)
    AutoCloseable { utilities.getMethod("removeFullScreenListenerFrom", java.awt.Window::class.java, listenerType).invoke(null, window, listener) }
}.getOrNull()
