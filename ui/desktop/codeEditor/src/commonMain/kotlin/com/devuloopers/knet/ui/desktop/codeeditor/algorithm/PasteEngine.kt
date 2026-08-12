package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import com.devuloopers.knet.ui.desktop.codeeditor.model.EditorCaretState

/**
 * Single-responsibility engine handling single and multi-line paste operations on a [DocumentBuffer].
 */
object PasteEngine {

    /**
     * Applies [pastedText] to [buffer] at line [lineIndex] and column [caretCol].
     *
     * @param buffer Mutable document buffer.
     * @param lineIndex 0-indexed line index where paste originates.
     * @param caretCol 0-indexed column offset where paste originates.
     * @param pastedText Raw text string pasted by the user.
     * @return Updated [EditorCaretState] reflecting the new caret location after paste insertion.
     */
    fun applyPaste(
        buffer: DocumentBuffer,
        lineIndex: Int,
        caretCol: Int,
        pastedText: String
    ): EditorCaretState {
        val currentLines = buffer.getLines()
        if (lineIndex !in currentLines.indices) return EditorCaretState(lineIndex, caretCol)

        val targetLine = currentLines[lineIndex]
        val safeCol = caretCol.coerceIn(0, targetLine.length)
        val before = targetLine.substring(0, safeCol)
        val after = targetLine.substring(safeCol)

        // Normalize Windows CR/LF and Mac CR newlines to standard LF
        val normalizedPastedText = pastedText.replace("\r\n", "\n").replace("\r", "\n")
        val pastedLines = normalizedPastedText.split("\n")

        if (pastedLines.size == 1) {
            // Single-line paste
            val newText = before + pastedLines[0] + after
            buffer.setLine(lineIndex, newText)
            return EditorCaretState(
                lineIndex = lineIndex,
                colIndex = before.length + pastedLines[0].length
            )
        } else {
            // Multi-line paste
            buffer.setLine(lineIndex, before + pastedLines.first())

            // Reconstruct full list of lines to inject into buffer
            val newDocumentLines = buffer.getLines().toMutableList()
            val middleAndEndLines = mutableListOf<String>()

            for (i in 1 until pastedLines.size - 1) {
                middleAndEndLines.add(pastedLines[i])
            }
            val lastPastedLineText = pastedLines.last() + after
            middleAndEndLines.add(lastPastedLineText)

            newDocumentLines.addAll(lineIndex + 1, middleAndEndLines)
            buffer.replaceAll(newDocumentLines)

            val finalLineIndex = lineIndex + pastedLines.size - 1
            val finalColIndex = pastedLines.last().length

            return EditorCaretState(
                lineIndex = finalLineIndex,
                colIndex = finalColIndex
            )
        }
    }
}
