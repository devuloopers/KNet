package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.component.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds

/**
 * Single-responsibility engine for extracting and manipulating multi-line selection ranges on a [DocumentBuffer].
 */
object SelectionEngine {


    /**
     * Extracts text spanning across [selection] from [buffer].
     *
     * @param buffer Document buffer to read from.
     * @param selection Multi-line or single-line selection range.
     * @return Extracted text snippet joined by newlines.
     */
    fun extractSelectedText(buffer: DocumentBuffer, selection: EditorSelection): String {
        if (selection.isEmpty) return ""
        val lines = buffer.getLines()
        val (startLine, startCol) = selection.normalizedStart
        val (endLine, endCol) = selection.normalizedEnd

        if (startLine !in lines.indices || endLine !in lines.indices) return ""

        if (startLine == endLine) {
            val line = lines[startLine]
            val safeStart = startCol.coerceIn(0, line.length)
            val safeEnd = endCol.coerceIn(0, line.length)
            return line.substring(safeStart, safeEnd)
        }

        val result = mutableListOf<String>()

        // First line segment (from startCol to end of line)
        val firstLine = lines[startLine]
        val safeStartCol = startCol.coerceIn(0, firstLine.length)
        result.add(firstLine.substring(safeStartCol))

        // Middle lines
        for (i in startLine + 1 until endLine) {
            result.add(lines[i])
        }

        // Last line segment (from 0 to endCol)
        val lastLine = lines[endLine]
        val safeEndCol = endCol.coerceIn(0, lastLine.length)
        result.add(lastLine.substring(0, safeEndCol))

        return result.joinToString("\n")
    }

    /**
     * Deletes the text spanning across [selection] from [buffer].
     *
     * @param buffer Document buffer to mutate.
     * @param selection Multi-line or single-line selection range to delete.
     * @return Updated [EditorCaretState] reflecting caret position after deletion.
     */
    fun deleteSelectedText(buffer: DocumentBuffer, selection: EditorSelection): EditorCaretState {
        if (selection.isEmpty) return EditorCaretState(selection.startLine, selection.startCol)
        val lines = buffer.getLines()
        val (startLine, startCol) = selection.normalizedStart
        val (endLine, endCol) = selection.normalizedEnd

        if (startLine !in lines.indices || endLine !in lines.indices) {
            return EditorCaretState(selection.startLine, selection.startCol)
        }

        if (startLine == endLine) {
            val line = lines[startLine]
            val safeStart = startCol.coerceIn(0, line.length)
            val safeEnd = endCol.coerceIn(0, line.length)
            val before = line.substring(0, safeStart)
            val after = line.substring(safeEnd)
            buffer.setLine(startLine, before + after)
            return EditorCaretState(startLine, safeStart)
        }

        val firstLine = lines[startLine]
        val lastLine = lines[endLine]
        val safeStartCol = startCol.coerceIn(0, firstLine.length)
        val safeEndCol = endCol.coerceIn(0, lastLine.length)

        val before = firstLine.substring(0, safeStartCol)
        val after = lastLine.substring(safeEndCol)

        val updatedLines = lines.toMutableList()
        // Remove lines from endLine down to startLine + 1
        for (i in endLine downTo startLine + 1) {
            updatedLines.removeAt(i)
        }
        updatedLines[startLine] = before + after

        buffer.replaceAll(updatedLines)
        return EditorCaretState(startLine, safeStartCol)
    }

    /**
     * Computes the line-level character selection bounds [com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds]
     * for a given line index [lineIndex] within [selection].
     *
     * Returns `null` if [lineIndex] is not covered by [selection].
     */
    fun computeLineBounds(
        selection: EditorSelection?,
        lineIndex: Int,
        lineLength: Int
    ): LineSelectionBounds? {
        if (selection == null || selection.isEmpty) return null
        if (!selection.containsLine(lineIndex)) return null

        val (startLine, startCol) = selection.normalizedStart
        val (endLine, endCol) = selection.normalizedEnd

        val lineStartCol: Int
        val lineEndCol: Int

        if (startLine == endLine) {
            lineStartCol = startCol.coerceIn(0, lineLength)
            lineEndCol = endCol.coerceIn(0, lineLength)
        } else {
            when (lineIndex) {
                startLine -> {
                    lineStartCol = startCol.coerceIn(0, lineLength)
                    lineEndCol = lineLength
                }
                endLine -> {
                    lineStartCol = 0
                    lineEndCol = endCol.coerceIn(0, lineLength)
                }
                else -> {
                    lineStartCol = 0
                    lineEndCol = lineLength
                }
            }
        }

        val effectiveEndCol = if (lineStartCol == lineEndCol && lineLength > 0) {
            lineEndCol
        } else {
            lineEndCol.coerceAtLeast(lineStartCol)
        }

        val isStart = (lineIndex == startLine)
        val isEnd = (lineIndex == endLine)
        val isMiddle = (lineIndex > startLine && lineIndex < endLine)

        return LineSelectionBounds(
            startCol = lineStartCol,
            endCol = effectiveEndCol,
            isStartLine = isStart,
            isEndLine = isEnd,
            isMiddleLine = isMiddle
        )
    }
}



