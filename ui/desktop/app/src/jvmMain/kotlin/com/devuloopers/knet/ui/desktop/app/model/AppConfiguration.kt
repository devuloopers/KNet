package com.devuloopers.knet.ui.desktop.app.model

/**
 * Data configuration DTO for setting up the Desktop application window and frame.
 *
 * @property appTitle Title string shown in window header.
 * @property initialWidth Default window width in dp.
 * @property initialHeight Default window height in dp.
 * @property isResizable Whether the window is user-resizable.
 * @property isAlwaysOnTop Whether the window floats above other application windows.
 */
data class AppConfiguration(
    val appTitle: String = "KNet — Desktop Proxy Studio",
    val initialWidth: Int = 1280,
    val initialHeight: Int = 800,
    val isResizable: Boolean = true,
    val isAlwaysOnTop: Boolean = false
)
