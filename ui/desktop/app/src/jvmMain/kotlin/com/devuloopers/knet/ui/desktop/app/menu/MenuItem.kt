package com.devuloopers.knet.ui.desktop.app.menu

/**
 * Data model for desktop menu items in `:ui:desktop:app`.
 *
 * @property id Identifier string.
 * @property label Title string.
 * @property shortcut Optional keyboard shortcut string (e.g. "Ctrl+S").
 * @property isEnabled Whether active and selectable.
 * @property onClick Action callback.
 */
public data class MenuItem(
    val id: String,
    val label: String,
    val shortcut: String? = null,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit
)
