package com.devuloopers.knet.ui.desktop.app.statusbar

import androidx.compose.ui.graphics.Color

/**
 * Data model for status bar items in `:ui:desktop:app`.
 *
 * @property id Identifier string.
 * @property label Field label text (e.g. "Uptime", "Clients").
 * @property value Value string to display (e.g. "00:12:34", "1 Client").
 * @property color Optional accent text color.
 */
public data class StatusItem(
    val id: String,
    val label: String,
    val value: String,
    val color: Color? = null
)
