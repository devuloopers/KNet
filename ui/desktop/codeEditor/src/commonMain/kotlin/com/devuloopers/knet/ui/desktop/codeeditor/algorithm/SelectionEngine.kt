package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState
import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorSelection
import com.devuloopers.knet.ui.desktop.codeeditor.model.LineSelectionBounds

/**
 * Single-responsibility engine for extracting and manipulating multi-line selection ranges on a [DocumentBuffer].
 *
 * Supports fold-aware selection expansion so that copying, cutting, or deleting a selection containing
 * a collapsed code block includes 100% of the block's text (header line + all hidden inner lines).
 */
object SelectionEngine {

    /**
     * Extracts text spanning across [selection] from [buffer], expanding collapsed fold regions if present.
     *
     * @param buffer Document buffer to read from.
     * @param selection Multi-line or single-line selection range.
     * @param foldRegions List of document fold regions.
     * @param collapsedFoldStartLines Set of 0-indexed start lines currently collapsed.
     * @return Extracted text snippet joined by newlines.
     */
    fun extractSelectedText(
        buffer: DocumentBuffer,
        selection: EditorSelection,
        foldRegions: List<FoldRegion> = emptyList(),
        collapsedFoldStartLines: Set<Int> = emptySet()
    ): String {
        if (selection.isEmpty) return ""
        val lines = buffer.getLines()
        val (normStartLine, normStartCol) = selection.normalizedStart
        val (normEndLine, normEndCol) = selection.normalizedEnd

        if (normStartLine !in lines.indices || normEndLine !in lines.indices) return ""

        // Expand fold range if selection starts on or covers collapsed fold regions
        val (startLine, startCol, endLine, endCol) = resolveEffectiveSelectionBounds(
            normStartLine = normStartLine,
            normStartCol = normStartCol,
            normEndLine = normEndLine,
            normEndCol = normEndCol,
            lines = lines,
            foldRegions = foldRegions,
            collapsedFoldStartLines = collapsedFoldStartLines
        )

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
     * Deletes the text spanning across [selection] from [buffer], expanding collapsed fold regions if present.
     *
     * @param buffer Document buffer to mutate.
     * @param selection Multi-line or single-line selection range to delete.
     * @param foldRegions List of document fold regions.
     * @param collapsedFoldStartLines Set of 0-indexed start lines currently collapsed.
     * @return Updated [EditorCaretState] reflecting caret position after deletion.
     */
    fun deleteSelectedText(
        buffer: DocumentBuffer,
        selection: EditorSelection,
        foldRegions: List<FoldRegion> = emptyList(),
        collapsedFoldStartLines: Set<Int> = emptySet()
    ): EditorCaretState {
        if (selection.isEmpty) return EditorCaretState(selection.startLine, selection.startCol)
        val lines = buffer.getLines()
        val (normStartLine, normStartCol) = selection.normalizedStart
        val (normEndLine, normEndCol) = selection.normalizedEnd

        if (normStartLine !in lines.indices || normEndLine !in lines.indices) {
            return EditorCaretState(selection.startLine, selection.startCol)
        }

        // Expand fold range if selection covers collapsed fold regions
        val (startLine, startCol, endLine, endCol) = resolveEffectiveSelectionBounds(
            normStartLine = normStartLine,
            normStartCol = normStartCol,
            normEndLine = normEndLine,
            normEndCol = normEndCol,
            lines = lines,
            foldRegions = foldRegions,
            collapsedFoldStartLines = collapsedFoldStartLines
        )

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
     * Resolves effective selection bounds by expanding collapsed fold blocks that overlap with the selection.
     */
    private fun resolveEffectiveSelectionBounds(
        normStartLine: Int,
        normStartCol: Int,
        normEndLine: Int,
        normEndCol: Int,
        lines: List<String>,
        foldRegions: List<FoldRegion>,
        collapsedFoldStartLines: Set<Int>
    ): FoldSelectionBounds {
        if (foldRegions.isEmpty() || collapsedFoldStartLines.isEmpty()) {
            return FoldSelectionBounds(normStartLine, normStartCol, normEndLine, normEndCol)
        }

        val foldByStart = foldRegions.associateBy { it.startLine }
        var effectiveEndLine = normEndLine
        var effectiveEndCol = normEndCol

        // Check all lines covered by normalized selection
        for (lineIdx in normStartLine..normEndLine) {
            if (lineIdx in collapsedFoldStartLines) {
                val region = foldByStart[lineIdx]
                if (region != null && region.endLine > effectiveEndLine) {
                    effectiveEndLine = region.endLine.coerceIn(0, lines.lastIndex)
                    effectiveEndCol = lines[effectiveEndLine].length
                }
            }
        }

        return FoldSelectionBounds(normStartLine, normStartCol, effectiveEndLine, effectiveEndCol)
    }

    private data class FoldSelectionBounds(
        val startLine: Int,
        val startCol: Int,
        val endLine: Int,
        val endCol: Int
    )

    /**
     * Computes the line-level character selection bounds [LineSelectionBounds]
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
