package com.devuloopers.knet.ui.desktop.codeeditor.model

import kotlin.math.max
import kotlin.math.min

/**
 * State model representing a 2D text selection range across lines and columns.
 *
 * @property startLine 0-indexed line index where selection originated.
 * @property startCol 0-indexed column index where selection originated.
 * @property endLine 0-indexed line index where selection currently ends.
 * @property endCol 0-indexed column index where selection currently ends.
 */
data class EditorSelection(
    val startLine: Int,
    val startCol: Int,
    val endLine: Int,
    val endCol: Int
) {
    /** True if the selection spans across more than one line. */
    val isMultiLine: Boolean
        get() = startLine != endLine

    /** True if the selection range has 0 length. */
    val isEmpty: Boolean
        get() = startLine == endLine && startCol == endCol

    /** Minimum line index covered by this selection range. */
    val minLine: Int
        get() = min(startLine, endLine)

    /** Maximum line index covered by this selection range. */
    val maxLine: Int
        get() = max(startLine, endLine)

    /**
     * Normalized start position (line, col) guaranteed to precede or equal end position.
     */
    val normalizedStart: Pair<Int, Int>
        get() {
            return if (startLine < endLine) {
                startLine to startCol
            } else if (startLine > endLine) {
                endLine to endCol
            } else {
                startLine to min(startCol, endCol)
            }
        }

    /**
     * Normalized end position (line, col) guaranteed to follow or equal start position.
     */
    val normalizedEnd: Pair<Int, Int>
        get() {
            return if (startLine < endLine) {
                endLine to endCol
            } else if (startLine > endLine) {
                startLine to startCol
            } else {
                startLine to max(startCol, endCol)
            }
        }

    /** Returns true if [lineIndex] is contained within [minLine]..[maxLine]. */
    fun containsLine(lineIndex: Int): Boolean = lineIndex in minLine..maxLine
}
