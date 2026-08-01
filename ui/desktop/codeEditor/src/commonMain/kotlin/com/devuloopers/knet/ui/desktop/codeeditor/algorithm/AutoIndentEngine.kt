package com.devuloopers.knet.ui.desktop.codeeditor.algorithm

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Intelligent line break auto-indentation engine.
 */
internal object AutoIndentEngine {

    fun handleInsertBreak(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue? {
        val oldText = oldValue.text
        val newText = newValue.text

        if (newText.length == oldText.length + 1 && newValue.selection.start == oldValue.selection.start + 1) {
            val insertedCharIndex = oldValue.selection.start
            if (insertedCharIndex in newText.indices && newText[insertedCharIndex] == '\n') {
                val previousLineStart = oldText.lastIndexOf('\n', insertedCharIndex - 1).let {
                    if (it == -1) 0 else it + 1
                }
                val previousLine = oldText.substring(previousLineStart, insertedCharIndex)

                val indentCount = previousLine.takeWhile { it == ' ' || it == '\t' }.length
                if (indentCount > 0) {
                    val indent = previousLine.substring(0, indentCount)
                    val sb = StringBuilder(newText)
                    sb.insert(insertedCharIndex + 1, indent)
                    val updatedText = sb.toString()
                    val newCaretPos = insertedCharIndex + 1 + indentCount
                    return TextFieldValue(text = updatedText, selection = TextRange(newCaretPos))
                }
            }
        }
        return null
    }
}
