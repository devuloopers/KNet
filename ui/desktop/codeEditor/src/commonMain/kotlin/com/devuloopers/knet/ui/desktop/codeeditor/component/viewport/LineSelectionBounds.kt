package com.devuloopers.knet.ui.desktop.codeeditor.component.viewport

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorSelection

/**
 * Character bounds used only while painting one selected viewport line.
 *
 * @property startColumn Inclusive selected text column.
 * @property endColumn Exclusive selected text column.
 * @property includesTrailingLineBreak Whether one character cell should represent the selected newline.
 */
internal data class LineSelectionBounds(
    val startColumn: Int,
    val endColumn: Int,
    val includesTrailingLineBreak: Boolean
) {
    /**
     * Returns whether this line has any stable selection paint.
     *
     * @param lineLength Current logical line length.
     * @return `true` for selected text or a selected trailing newline.
     */
    fun hasVisibleSelection(lineLength: Int): Boolean {
        val safeStart = startColumn.coerceIn(0, lineLength)
        val safeEnd = endColumn.coerceIn(safeStart, lineLength)
        return safeStart < safeEnd || includesTrailingLineBreak
    }
}

/** Projects a document selection onto one logical line for viewport painting. */
internal fun EditorSelection?.boundsForLine(lineIndex: Int, lineLength: Int): LineSelectionBounds? {
    val selection = this?.takeUnless(EditorSelection::isEmpty) ?: return null
    val range = selection.range
    if (!selection.containsLine(lineIndex)) return null

    val startColumn: Int
    val endColumn: Int
    if (range.start.line == range.end.line) {
        startColumn = range.start.column.coerceIn(0, lineLength)
        endColumn = range.end.column.coerceIn(0, lineLength)
    } else {
        when (lineIndex) {
            range.start.line -> {
                startColumn = range.start.column.coerceIn(0, lineLength)
                endColumn = lineLength
            }
            range.end.line -> {
                startColumn = 0
                endColumn = range.end.column.coerceIn(0, lineLength)
            }
            else -> {
                startColumn = 0
                endColumn = lineLength
            }
        }
    }

    return LineSelectionBounds(
        startColumn = startColumn,
        endColumn = endColumn.coerceAtLeast(startColumn),
        includesTrailingLineBreak = lineIndex < range.end.line
    ).takeIf { it.hasVisibleSelection(lineLength) }
}
