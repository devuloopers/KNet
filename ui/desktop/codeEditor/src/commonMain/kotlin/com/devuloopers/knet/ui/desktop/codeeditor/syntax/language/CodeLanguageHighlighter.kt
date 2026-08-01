package com.devuloopers.knet.ui.desktop.codeeditor.syntax.language

import androidx.compose.ui.text.AnnotatedString

/**
 * Strategy interface for syntax highlighting and code folding across various document languages.
 */
internal interface CodeLanguageHighlighter {
    /**
     * Unique identifier for this language highlighter (e.g., "json", "html", "xml", "plain").
     */
    val languageId: String

    /**
     * Calculates line fold ranges matching opening and closing structures (brackets, tags).
     */
    fun calculateFoldRanges(lines: List<String>): Map<Int, Int>

    /**
     * Resolves matching closing symbol representation when a block is collapsed.
     */
    fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String

    /**
     * Highlights a single line of text into a styled AnnotatedString.
     */
    fun highlightLine(lineText: String): AnnotatedString
}
