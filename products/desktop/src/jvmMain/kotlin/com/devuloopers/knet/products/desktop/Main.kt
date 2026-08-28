package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.products.desktop.bootstrap.DesktopBootstrap
import com.devuloopers.knet.domain.config.AppMetadata

/**
 * Desktop JVM application entry point.
 */
fun main() {
    System.setProperty("apple.awt.application.name", AppMetadata.APP_NAME)
    enableSynchronousMetalWindowResize()
    DesktopBootstrap.start()
}

/**
 * Keeps the native macOS window and the Compose Metal surface in the same live-resize transaction.
 * This property must be configured before Compose creates its first desktop window.
 */
private fun enableSynchronousMetalWindowResize() {
    if (System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)) {
        System.setProperty("skiko.rendering.macos.metalSynchronousLiveResize", "true")
    }
}
