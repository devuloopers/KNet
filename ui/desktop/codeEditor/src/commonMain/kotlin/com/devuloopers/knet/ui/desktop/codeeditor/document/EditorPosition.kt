package com.devuloopers.knet.ui.desktop.codeeditor.document

/**
 * Zero-based location inside an editor document.
 *
 * @property line Zero-based logical line index.
 * @property column Zero-based UTF-16 character offset inside [line].
 */
data class EditorPosition(
    val line: Int,
    val column: Int
) : Comparable<EditorPosition> {
    init {
        require(line >= 0) { "Editor line must be non-negative." }
        require(column >= 0) { "Editor column must be non-negative." }
    }

    /**
     * Orders positions by line and then by column.
     *
     * @param other Position to compare with this position.
     * @return Negative, zero, or positive according to document order.
     */
    override fun compareTo(other: EditorPosition): Int {
        val lineComparison = line.compareTo(other.line)
        return if (lineComparison != 0) lineComparison else column.compareTo(other.column)
    }
}

/**
 * Half-open document range from [start] inclusive to [end] exclusive.
 *
 * @property start First position included in the range.
 * @property end Position immediately after the range.
 */
data class EditorRange(
    val start: EditorPosition,
    val end: EditorPosition
) {
    init {
        require(start <= end) { "Editor range start must not be after its end." }
    }

    /** Returns `true` when this range contains no characters. */
    val isEmpty: Boolean
        get() = start == end

    companion object {
        /**
         * Creates a caret range containing no characters.
         *
         * @param position Caret position represented by the returned range.
         * @return Empty range at [position].
         */
        fun caret(position: EditorPosition): EditorRange = EditorRange(position, position)
    }
}

/**
 * Directional editor selection with a stable [anchor] and moving [active] endpoint.
 *
 * Keeping direction separate from the normalized [range] is required for correct Shift+Click and
 * drag behavior. Consumers that only need selected content should use [range].
 *
 * @property anchor Position where selection began.
 * @property active Current moving endpoint and caret position.
 */
data class EditorSelection(
    val anchor: EditorPosition,
    val active: EditorPosition
) {
    /** Normalized half-open range covered by this selection. */
    val range: EditorRange
        get() = if (anchor <= active) EditorRange(anchor, active) else EditorRange(active, anchor)

    /** Returns `true` when anchor and active endpoint are equal. */
    val isEmpty: Boolean
        get() = anchor == active

    /** Returns `true` when [lineIndex] lies inside the normalized selected line span. */
    fun containsLine(lineIndex: Int): Boolean = lineIndex in range.start.line..range.end.line
}
