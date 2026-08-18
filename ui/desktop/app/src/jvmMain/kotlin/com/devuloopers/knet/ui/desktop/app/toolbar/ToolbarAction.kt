package com.devuloopers.knet.ui.desktop.app.toolbar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data model for toolbar actions in `:ui:desktop:app`.
 *
 * @property id Action identifier string.
 * @property label Button label text.
 * @property icon Optional vector icon.
 * @property isEnabled Whether the action button is active and clickable.
 * @property color Optional accent color for the button text and icon.
 * @property onClick Callback triggered when clicked.
 */
data class ToolbarAction(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val isEnabled: Boolean = true,
    val color: Color? = null,
    val onClick: () -> Unit
)
