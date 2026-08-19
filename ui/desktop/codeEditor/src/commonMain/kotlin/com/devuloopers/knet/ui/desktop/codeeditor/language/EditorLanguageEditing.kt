package com.devuloopers.knet.ui.desktop.codeeditor.language

import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorEditKind
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorPosition
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorRange
import com.devuloopers.knet.ui.desktop.codeeditor.document.EditorTextEdit
import com.devuloopers.knet.ui.desktop.codeeditor.session.EditorSession

/** Language-aware editing operations shared by keyboard and future command-palette adapters. */
object EditorLanguageEditing {
    /**
     * Applies a line update, automatically inserting a registered closing bracket when applicable.
     *
     * @param session Target editor session.
     * @param lineIndex Zero-based edited line.
     * @param newText Text emitted by the active line input.
     * @param bracketProvider Optional active language bracket capability.
     * @return `true` when the line changed.
     */
    fun applyLineChange(
        session: EditorSession,
        lineIndex: Int,
        newText: String,
        bracketProvider: EditorBracketProvider?
    ): Boolean {
        if (lineIndex !in 0 until session.snapshot.lineCount) return false
        val oldText = session.snapshot.line(lineIndex)
        if (oldText == newText) return false
        val inserted = detectSingleInsertion(oldText, newText)
        val closing = inserted?.character?.let { opening ->
            bracketProvider?.pairs?.firstOrNull { it.opening == opening }?.closing
        }
        if (inserted != null && closing != null) {
            val position = EditorPosition(lineIndex, inserted.column)
            session.apply(
                edit = EditorTextEdit(
                    range = EditorRange.caret(position),
                    replacement = "${inserted.character}$closing",
                    kind = EditorEditKind.Structural
                ),
                caretAfter = EditorPosition(lineIndex, inserted.column + 1)
            )
            return true
        }
        return session.replaceLine(lineIndex, newText) != null
    }

    /**
     * Toggles the active language's line or block comment across the selection or current line.
     *
     * @param session Target editor session.
     * @param comments Active language comment configuration.
     * @return `true` when a supported comment edit was applied.
     */
    fun toggleComment(session: EditorSession, comments: EditorCommentConfiguration?): Boolean {
        val configuration = comments ?: return false
        val linePrefix = configuration.linePrefix?.takeIf(String::isNotEmpty)
        return if (linePrefix != null) {
            toggleLineComment(session, linePrefix)
        } else {
            toggleBlockComment(session, configuration)
        }
    }

    private fun toggleLineComment(session: EditorSession, prefix: String): Boolean {
        val snapshot = session.snapshot
        val range = session.selection?.range ?: EditorRange.caret(session.caret)
        val firstLine = range.start.line
        val inclusiveEndLine = if (range.end.column == 0 && range.end.line > firstLine) {
            range.end.line - 1
        } else {
            range.end.line
        }
        val lastLine = inclusiveEndLine.coerceAtMost(snapshot.lineCount - 1)
        val nonBlankLines = (firstLine..lastLine).filter { snapshot.line(it).isNotBlank() }
        if (nonBlankLines.isEmpty()) return false
        val removePrefix = nonBlankLines.all { lineIndex ->
            val line = snapshot.line(lineIndex)
            line.startsWith(prefix, line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0))
        }
        val edits = nonBlankLines.map { lineIndex ->
            val line = snapshot.line(lineIndex)
            val indentationLength = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            if (removePrefix) {
                var removalEnd = indentationLength + prefix.length
                if (line.getOrNull(removalEnd) == ' ') removalEnd++
                EditorTextEdit(
                    EditorRange(
                        EditorPosition(lineIndex, indentationLength),
                        EditorPosition(lineIndex, removalEnd)
                    ),
                    replacement = "",
                    kind = EditorEditKind.Structural
                )
            } else {
                EditorTextEdit(
                    EditorRange.caret(EditorPosition(lineIndex, indentationLength)),
                    replacement = "$prefix ",
                    kind = EditorEditKind.Structural
                )
            }
        }
        return session.applyBatch(edits).isNotEmpty()
    }

    private fun toggleBlockComment(
        session: EditorSession,
        comments: EditorCommentConfiguration
    ): Boolean {
        val blockStart = comments.blockStart ?: return false
        val blockEnd = comments.blockEnd ?: return false
        val snapshot = session.snapshot
        val range = session.selection?.range ?: run {
            val line = session.caret.line
            EditorRange(EditorPosition(line, 0), EditorPosition(line, snapshot.line(line).length))
        }
        val selectedText = snapshot.text(range)
        val replacement = if (selectedText.startsWith(blockStart) && selectedText.endsWith(blockEnd)) {
            selectedText.removePrefix(blockStart).removeSuffix(blockEnd)
        } else {
            blockStart + selectedText + blockEnd
        }
        session.apply(EditorTextEdit(range, replacement, EditorEditKind.Structural))
        return true
    }

    private fun detectSingleInsertion(oldText: String, newText: String): SingleCharacterInsertion? {
        if (newText.length != oldText.length + 1) return null
        var column = 0
        while (column < oldText.length && oldText[column] == newText[column]) column++
        if (oldText.substring(column) != newText.substring(column + 1)) return null
        return SingleCharacterInsertion(column, newText[column])
    }

    private data class SingleCharacterInsertion(val column: Int, val character: Char)
}
