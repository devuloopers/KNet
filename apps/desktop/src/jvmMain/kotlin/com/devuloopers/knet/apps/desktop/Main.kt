package com.devuloopers.knet.apps.desktop

import com.devuloopers.knet.apps.desktop.bootstrap.DesktopBootstrap
import com.devuloopers.knet.domain.config.AppMetadata

/**
 * Desktop JVM application entry point.
 */
fun main() {
    System.setProperty("apple.awt.application.name", AppMetadata.APP_NAME)
    DesktopBootstrap.start()
}
