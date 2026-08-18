package com.devuloopers.knet.products.desktop

import com.devuloopers.knet.products.desktop.bootstrap.DesktopBootstrap
import com.devuloopers.knet.domain.config.AppMetadata

/**
 * Desktop JVM application entry point.
 */
fun main() {
    System.setProperty("apple.awt.application.name", AppMetadata.APP_NAME)
    DesktopBootstrap.start()
}
