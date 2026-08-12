package com.devuloopers.knet.ui.desktop.codeeditor.model

/**
 * Line-level character selection bounds representing start/end columns and line position flags.
 *
 * @property startCol 0-indexed starting character column on this line.
 * @property endCol 0-indexed ending character column on this line.
 * @property isStartLine True if this line is the origin line of a selection range.
 * @property isEndLine True if this line is the target end line of a selection range.
 * @property isMiddleLine True if this line is an intermediate line strictly enclosed between start and end lines.
 */
data class LineSelectionBounds(
    val startCol: Int,
    val endCol: Int,
    val isStartLine: Boolean = false,
    val isEndLine: Boolean = false,
    val isMiddleLine: Boolean = false
)
