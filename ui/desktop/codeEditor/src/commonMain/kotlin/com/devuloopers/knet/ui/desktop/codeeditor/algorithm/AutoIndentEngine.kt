package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Smart Auto-Indentation Engine inspired by RSyntaxTextArea EditorKit (InsertBreakAction).
 *
 * Handles Enter/Newline keystrokes in code editors by calculating indentation inheritance,
 * opening bracket (+2 space) increases, and bracket pair expansion (`{ | }`).
 */
object AutoIndentEngine {

    /**
     * Inspects a text value change and formats newlines with smart indentation if Enter was pressed.
     *
     * @param oldValue The previous [TextFieldValue] before mutation.
     * @param newValue The new [TextFieldValue] containing user input.
     * @param tabSize Number of indentation spaces per tab level (defaults to 2 spaces).
     * @return Formatted [TextFieldValue] with updated text and caret selection range, or `null` if not a newline insertion.
     */
    fun handleInsertBreak(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        tabSize: Int = 2
    ): TextFieldValue? {
        val oldText = oldValue.text
        val newText = newValue.text

        // Check if text grew by 1 character and inserted a '\n'
        if (newText.length != oldText.length + 1) return null

        val insertedOffset = newValue.selection.start - 1
        if (insertedOffset < 0 || insertedOffset >= newText.length) return null
        if (newText[insertedOffset] != '\n') return null

        val textBeforeCaret = oldText.substring(0, insertedOffset)
        val textAfterCaret = oldText.substring(insertedOffset)

        // Find current line text before caret
        val lastNewlineIndex = textBeforeCaret.lastIndexOf('\n')
        val currentLine = if (lastNewlineIndex != -1) {
            textBeforeCaret.substring(lastNewlineIndex + 1)
        } else {
            textBeforeCaret
        }

        // Phase 1: Indentation Inheritance
        val leadingWhitespace = currentLine.takeWhile { it == ' ' || it == '\t' }
        val trimmedLine = currentLine.trimEnd()

        // Phase 2: Opening Bracket & Colon Detection
        val shouldIncreaseIndent = trimmedLine.endsWith("{") ||
                trimmedLine.endsWith("[") ||
                trimmedLine.endsWith("(") ||
                trimmedLine.endsWith(":")

        val indentSpaces = " ".repeat(tabSize)
        val nextIndent = leadingWhitespace + (if (shouldIncreaseIndent) indentSpaces else "")

        // Phase 3: Bracket Pair Expansion ({ | })
        val charBefore = textBeforeCaret.lastOrNull()
        val charAfter = textAfterCaret.firstOrNull()

        val isBracketExpansion = (charBefore == '{' && charAfter == '}') ||
                (charBefore == '[' && charAfter == ']') ||
                (charBefore == '(' && charAfter == ')')

        return if (isBracketExpansion) {
            val formattedText = textBeforeCaret + "\n" + leadingWhitespace + indentSpaces + "\n" + leadingWhitespace + textAfterCaret
            val caretPos = (textBeforeCaret + "\n" + leadingWhitespace + indentSpaces).length
            TextFieldValue(
                text = formattedText,
                selection = TextRange(caretPos)
            )
        } else {
            val formattedText = textBeforeCaret + "\n" + nextIndent + textAfterCaret
            val caretPos = (textBeforeCaret + "\n" + nextIndent).length
            TextFieldValue(
                text = formattedText,
                selection = TextRange(caretPos)
            )
        }
    }

    /**
     * Computes the indentation prefix for a new line created by splitting [lineText] at [colIndex].
     *
     * Used by [com.devuloopers.knet.ui.desktop.codeeditor.algorithm.DocumentBuffer.splitLine]
     * to determine the leading whitespace of the newly created trailing line when the user
     * presses Enter inside [com.devuloopers.knet.ui.desktop.codeeditor.component.viewport.EditableLineContent].
     *
     * Applies the same three-phase indentation logic as [handleInsertBreak]:
     * - **Phase 1**: Inherits leading whitespace from the current line.
     * - **Phase 2**: Increases indentation by [tabSize] spaces if the character before [colIndex]
     *   is an opening bracket (`{`, `[`, `(`) or colon (`:`).
     * - **Phase 3**: Detects bracket pair expansion (`{ | }`, `[ | ]`, `( | )`) — when the character
     *   immediately before [colIndex] is an opening bracket and the character at [colIndex] is
     *   the matching closing bracket, returns the inner indented prefix only (the closing bracket
     *   line is managed separately by the caller via a second [DocumentBuffer.splitLine] call).
     *
     * @param lineText Full text content of the line being split.
     * @param colIndex 0-indexed column position at which Enter was pressed.
     * @param tabSize Number of spaces per indentation level (defaults to 2).
     * @return Indentation prefix string to prepend to the new trailing line.
     */
    fun computeIndentForSplit(
        lineText: String,
        colIndex: Int,
        tabSize: Int = 2
    ): String {
        val safeCol = colIndex.coerceIn(0, lineText.length)
        val textBefore = lineText.substring(0, safeCol)

        // Phase 1: Inherit leading whitespace from the current line.
        val leadingWhitespace = lineText.takeWhile { it == ' ' || it == '\t' }
        val trimmedBefore = textBefore.trimEnd()

        // Phase 2: Increase indentation if line ends with an opening bracket or colon.
        val shouldIncreaseIndent = trimmedBefore.endsWith("{") ||
                trimmedBefore.endsWith("[") ||
                trimmedBefore.endsWith("(") ||
                trimmedBefore.endsWith(":")

        val indentSpaces = " ".repeat(tabSize)
        return leadingWhitespace + (if (shouldIncreaseIndent) indentSpaces else "")
    }
}
