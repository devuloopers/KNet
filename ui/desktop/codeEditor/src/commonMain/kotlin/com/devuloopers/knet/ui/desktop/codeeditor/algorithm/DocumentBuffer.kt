package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

/**
 * Line-indexed mutable document buffer for the KNet virtualized code editor.
 *
 * Replaces the monolithic [androidx.compose.ui.text.input.TextFieldValue] document model
 * with a [MutableList]-backed line buffer where every edit operation is scoped to individual
 * line indices. This ensures that a single character keystroke on line N never allocates or
 * re-measures any other line in the document.
 *
 * Thread safety: [DocumentBuffer] is not thread-safe. All mutations must occur on the UI thread
 * via Compose state callbacks. Read-only snapshots via [getLines] may be consumed on any thread.
 *
 * @param initialLines Initial document content split into lines. An empty buffer is initialized
 *   with a single empty string to guarantee at least one editable line always exists.
 */
class DocumentBuffer(initialLines: List<String>) {

    private val lines: MutableList<String> =
        if (initialLines.isEmpty()) mutableListOf("") else initialLines.toMutableList()

    /**
     * Returns an immutable snapshot of the current document lines.
     *
     * The returned list is a detached copy safe to pass to [LazyCodeBody] as [rawLines]
     * without risking concurrent mutation issues.
     *
     * @return Ordered list of line strings from line 0 to [lineCount] - 1.
     */
    fun getLines(): List<String> = lines.toList()

    /**
     * Returns the total number of lines currently in the document buffer.
     */
    fun lineCount(): Int = lines.size

    /**
     * Replaces the text content of a single line.
     *
     * Called when the user types or deletes characters within a single line's
     * [EditableLineContent] [BasicTextField]. No other lines are affected.
     *
     * @param index 0-indexed line position to update.
     * @param text New text content for this line.
     */
    fun setLine(index: Int, text: String) {
        if (index in lines.indices) {
            lines[index] = text
        }
    }

    /**
     * Splits a single line into two lines at the given column position.
     *
     * Called when the user presses Enter inside a line. The text before [col] remains
     * on line [index], and the text from [col] onwards (prefixed by [trailingIndent])
     * is inserted as a new line at [index] + 1.
     *
     * @param index 0-indexed line position to split.
     * @param col 0-indexed column position within the line where the split occurs.
     * @param trailingIndent Indentation prefix to prepend to the newly created trailing line.
     *   Computed by [AutoIndentEngine.computeIndentForSplit].
     */
    fun splitLine(index: Int, col: Int, trailingIndent: String = "") {
        if (index !in lines.indices) return
        val line = lines[index]
        val safeCol = col.coerceIn(0, line.length)
        val before = line.substring(0, safeCol)
        val after = line.substring(safeCol)
        lines[index] = before
        lines.add(index + 1, trailingIndent + after)
    }

    /**
     * Merges a line with its preceding line.
     *
     * Called when the user presses Backspace at column 0 of line [index]. The text of
     * line [index] is appended to the end of line [index] - 1, and line [index] is removed.
     * No-op if [index] is 0 (first line has no preceding line to merge into).
     *
     * @param index 0-indexed line position to merge into the preceding line.
     */
    fun mergeLines(index: Int) {
        if (index <= 0 || index !in lines.indices) return
        val previousLine = lines[index - 1]
        val currentLine = lines[index]
        lines[index - 1] = previousLine + currentLine
        lines.removeAt(index)
    }

    /**
     * Replaces the entire document content with a new set of lines.
     *
     * Used for bulk document replacement operations such as undo/redo restoration,
     * external code format application, or initial document loading from external state.
     *
     * @param newLines New complete document line list. An empty list is treated as a
     *   single empty line to maintain the invariant of at least one editable line.
     */
    fun replaceAll(newLines: List<String>) {
        lines.clear()
        if (newLines.isEmpty()) {
            lines.add("")
        } else {
            lines.addAll(newLines)
        }
    }

    /**
     * Returns the full document text by joining all lines with newline separators.
     *
     * Used to produce the payload for [EditorMode.Editable.onCodeChange] callbacks
     * and [UndoRedoStack] snapshot storage.
     *
     * @return Full document string with lines joined by `\n`.
     */
    fun toFullText(): String = lines.joinToString("\n")
}
