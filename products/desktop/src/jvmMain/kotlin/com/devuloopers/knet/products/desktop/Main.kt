package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.domain.config.AppMetadata
import com.devuloopers.knet.products.desktop.bootstrap.DesktopBootstrap

/**
 * Desktop JVM application entry point.
 *
 * Configures platform-specific hardware acceleration and rendering pipeline properties
 * prior to Compose Desktop window creation, then starts the [DesktopBootstrap] lifecycle.
 */
fun main() {
    configurePlatformRendering()
    DesktopBootstrap.start()
}

/**
 * Configures platform-specific rendering flags, hardware acceleration pipelines,
 * and live window resizing optimizations before Compose Desktop initializes its first window.
 *
 * @param osName Operating system name string, defaulting to the JVM `os.name` system property.
 */
fun configurePlatformRendering(osName: String = System.getProperty("os.name").orEmpty()) {
    System.setProperty("apple.awt.application.name", AppMetadata.APP_NAME)
    enableSynchronousMetalWindowResize(osName)
    configureWindowsRenderingPipeline(osName)
}

/**
 * Keeps the native macOS window and the Compose Metal surface in the same live-resize transaction.
 * This property must be configured before Compose creates its first desktop window.
 *
 * @param osName Operating system name string.
 */
fun enableSynchronousMetalWindowResize(osName: String = System.getProperty("os.name").orEmpty()) {
    if (osName.contains("mac", ignoreCase = true)) {
        System.setProperty("skiko.rendering.macos.metalSynchronousLiveResize", "true")
    }
}

/**
 * Configures hardware acceleration and Java2D pipeline settings on Windows
 * to minimize swapchain presentation latency and eliminate visual stretching artifacts during live window resizing.
 *
 * Defaults to the OpenGL rendering backend with FrameBuffer Object acceleration and unconstrained VSync presentation,
 * preventing AWT background erasure and eliminating resize debounce stalls.
 *
 * @param osName Operating system name string.
 * @param preferredRenderApi Preferred Skiko rendering backend name, defaulting to existing `skiko.renderApi` system property or "OPENGL".
 */
fun configureWindowsRenderingPipeline(
    osName: String = System.getProperty("os.name").orEmpty(),
    preferredRenderApi: String = System.getProperty("skiko.renderApi") ?: "OPENGL"
) {
    if (osName.contains("windows", ignoreCase = true)) {
        System.setProperty("skiko.renderApi", preferredRenderApi)
        System.setProperty("skiko.vsync.enabled", "false")
        System.setProperty("sun.java2d.noddraw", "true")
        System.setProperty("sun.awt.noerasebackground", "true")
        System.setProperty("sun.awt.erasebackgroundonresize", "false")

        if (preferredRenderApi.equals("OPENGL", ignoreCase = true)) {
            System.setProperty("sun.java2d.opengl", "true")
            System.setProperty("sun.java2d.opengl.fbobject", "true")
            System.setProperty("sun.java2d.d3d", "false")
        } else if (preferredRenderApi.equals("DIRECTX", ignoreCase = true)) {
            System.setProperty("sun.java2d.d3d", "true")
            System.setProperty("sun.java2d.opengl", "false")
        }
    }
}

