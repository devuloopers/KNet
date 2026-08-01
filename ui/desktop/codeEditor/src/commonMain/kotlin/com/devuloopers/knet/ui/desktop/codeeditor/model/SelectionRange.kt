package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * Represents selected character offsets in text.
 */
data class SelectionRange(
    val start: Int = 0,
    val end: Int = 0
) {
    val length: Int get() = (end - start).coerceAtLeast(0)
    val isEmpty: Boolean get() = start == end
}
