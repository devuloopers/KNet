package com.devuloopers.knet.ui.desktop.codeeditor.syntax

import androidx.compose.ui.text.AnnotatedString

/**
 * Fallback highlighter strategy for plain text, raw logs, and unformatted content.
 */
class PlainTextLanguageHighlighter : CodeLanguageHighlighter {
    override val languageId: String = "plain"

    override fun calculateFoldRanges(lines: List<String>): Map<Int, Int> = emptyMap()

    override fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String = ""

    override fun highlightLine(lineText: String): AnnotatedString {
        return AnnotatedString(lineText)
    }
}
