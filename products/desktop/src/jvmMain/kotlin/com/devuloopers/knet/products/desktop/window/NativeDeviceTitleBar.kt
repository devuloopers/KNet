package com.devuloopers.knet.products.desktop.window

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

/**
 * Uses native macOS/Windows title-bar integration, retaining system controls and window gestures.
 * Unsupported runtimes keep their complete native caption with the same device strip immediately below it.
 * No software imitation of close, zoom, minimize, drag or macOS double-click preferences is installed.
 */
@Composable
internal fun FrameWindowScope.NativeDeviceTitleBar(content: @Composable NativeTitleBarScope.() -> Unit) {
    val height = KNetTheme.dimensions.workspaceTabHeight
    val fallbackInset = KNetTheme.dimensions.nativeWindowControlsInset
    val windowsFallbackInset = KNetTheme.dimensions.windowsWindowControlsInset
    val density = LocalDensity.current
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val titleBarHandle = remember(window) {
        if (supportsNativeDeviceTitleBar(System.getProperty("os.name"), JBR.isWindowDecorationsSupported())) {
            runCatching { JBR.getWindowDecorations().createCustomTitleBar().apply {
                this.height = height.value
                if (isWindows) putProperty("controls.dark", true)
            } }.getOrNull()?.let(::NativeTitleBarHandle)
        } else null
    }
    val titleBar = titleBarHandle?.api
    var installed by remember(window, titleBarHandle) { mutableStateOf(false) }
    var nativeInsets by remember(window, titleBarHandle) { mutableStateOf(0f to 0f) }
    var measuredHeight by remember(window) { mutableFloatStateOf(height.value) }

    fun applyTitleBar() {
        if (titleBar == null) return
        // Follow Jewel's layout-owned application: the Skia child must have its final geometry before
        // JBR positions its layered native controls. Equal-size Compose recompositions do no native work.
        installed = runCatching {
            titleBar.height = measuredHeight
            JBR.getWindowDecorations().setCustomTitleBar(window, titleBar)
            nativeInsets = titleBar.leftInset to titleBar.rightInset
            true
        }.getOrDefault(false)
    }
    DisposableEffect(window, titleBarHandle) {
        var disposed = false
        // Peer insets settle after resize/move dispatch, including a move to a monitor with a new DPI.
        fun refreshInsets() {
            SwingUtilities.invokeLater {
                if (!disposed && installed && titleBar != null) nativeInsets = titleBar.leftInset to titleBar.rightInset
            }
        }
        val listener = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) { refreshInsets() }
            override fun componentShown(event: ComponentEvent) {
                SwingUtilities.invokeLater { if (!disposed) applyTitleBar() }
            }
            override fun componentMoved(event: ComponentEvent) { refreshInsets() }
        }
        val graphicsListener = PropertyChangeListener { refreshInsets() }
        if (titleBar != null) {
            window.addComponentListener(listener)
            window.addPropertyChangeListener("graphicsConfiguration", graphicsListener)
        }
        onDispose {
            disposed = true
            window.removeComponentListener(listener)
            window.removePropertyChangeListener("graphicsConfiguration", graphicsListener)
            if (installed) runCatching { JBR.getWindowDecorations().setCustomTitleBar(window, null as WindowDecorations.CustomTitleBar?) }
            installed = false
        }
    }
    var fullscreen by remember(window) { mutableStateOf(false) }
    DisposableEffect(window, titleBarHandle, installed) {
        val registration = if (installed && !isWindows) observeMacFullscreen(window) { fullscreen = it } else null
        onDispose { registration?.close() }
    }
    val regions = remember(window) { NativeTitleBarRegions() }
    DisposableEffect(window, titleBarHandle, installed, density) {
        val transform = window.graphicsConfiguration?.defaultTransform
        val scaleX = (transform?.scaleX ?: 1.0).toFloat()
        val scaleY = (transform?.scaleY ?: 1.0).toFloat()
        val listener = object : MouseAdapter() {
            private fun updateHitTest(event: MouseEvent) {
                titleBar?.forceHitTest(regions.isInteractive(event.x * scaleX, event.y * scaleY))
            }
            override fun mousePressed(event: MouseEvent) = updateHitTest(event)
            override fun mouseReleased(event: MouseEvent) = updateHitTest(event)
            override fun mouseDragged(event: MouseEvent) = updateHitTest(event)
            override fun mouseMoved(event: MouseEvent) = updateHitTest(event)
        }
        // ComposeWindow forwards these window-scoped listeners to its Skia child, as Jewel does.
        // Keep hit testing in the same component dispatch path used by the reference implementation.
        if (installed && titleBar != null) {
            window.addMouseListener(listener)
            window.addMouseMotionListener(listener)
        }
        onDispose {
            window.removeMouseListener(listener)
            window.removeMouseMotionListener(listener)
        }
    }
    val insets = nativeTitleBarInsets(isWindows, installed, fullscreen, nativeInsets.first, nativeInsets.second,
        fallbackInset.value, windowsFallbackInset.value)
    NativeTitleBarScope(insets.start.dp, insets.end.dp, regions::update) { logicalHeight ->
        if (logicalHeight > 0f) {
            measuredHeight = logicalHeight
            applyTitleBar()
        }
    }.content()
}

/**
 * Identity key for one native binding. JBR's generated API proxy has non-reflexive equality on supported
 * runtimes (`api.equals(api)` is false), so it must never be passed directly to remember/DisposableEffect.
 * This holder keeps the native binding mounted until its owning window is disposed.
 */
internal class NativeTitleBarHandle(val api: WindowDecorations.CustomTitleBar)

/**
 * Supplies native control insets and a layout-owned title-bar host to the application shell.
 * @property insetStart Logical space occupied by native controls at the start edge.
 * @property insetEnd Logical space occupied by native controls at the end edge.
 * @property onInteractiveBoundsChanged Receives the current Compose client rectangles in window pixels.
 */
internal class NativeTitleBarScope(
    val insetStart: Dp,
    val insetEnd: Dp,
    val onInteractiveBoundsChanged: (List<Rect>) -> Unit,
    private val onHeightMeasured: (Float) -> Unit,
) {
    /** Apply native geometry only after Compose measures the actual strip, including a DPI/size change. */
    @Composable
    fun TitleBar(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val currentHeightMeasured by rememberUpdatedState(onHeightMeasured)
        val sizeChanged: (IntSize) -> Unit = remember(density) {
            { size -> currentHeightMeasured(size.height / density.density) }
        }
        Box(Modifier.onSizeChanged(sizeChanged)) { content() }
    }
}

/** Native decoration support is required; Linux retains its system title bar. */
internal fun supportsNativeDeviceTitleBar(osName: String, runtimeSupported: Boolean): Boolean =
    runtimeSupported && (osName.startsWith("Mac", ignoreCase = true) || osName.startsWith("Windows", ignoreCase = true))

/** Immutable bounded hit-test snapshot shared between Compose layout and AWT dispatch without a component listener leak. */
internal class NativeTitleBarRegions {
    @Volatile private var regions: List<Rect> = emptyList()
    fun update(bounds: List<Rect>) { regions = bounds.take(4096).toList() }
    fun isInteractive(x: Float, y: Float): Boolean = regions.any { it.contains(androidx.compose.ui.geometry.Offset(x, y)) }
}

/** Fullscreen can remove the control reservation; unsupported runtimes need no custom inset. */
internal fun nativeControlInset(supported: Boolean, fullscreen: Boolean, nativeInset: Float, fallback: Float): Float =
    if (!supported || fullscreen) 0f else maxOf(nativeInset, fallback)

/** Reserve native control space on the OS side, including before the peer reports its actual inset. */
internal fun nativeTitleBarInsets(isWindows: Boolean, installed: Boolean, fullscreen: Boolean,
    leftInset: Float, rightInset: Float, macFallback: Float, windowsFallback: Float): NativeTitleBarInsets =
    if (isWindows) NativeTitleBarInsets(0f, nativeControlInset(installed, false, rightInset, windowsFallback))
    else NativeTitleBarInsets(nativeControlInset(installed, fullscreen, leftInset, macFallback), 0f)

/** Logical content insets reserved for OS-owned caption controls, separate from Compose hit-test pixels. */
internal data class NativeTitleBarInsets(val start: Float, val end: Float)

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
