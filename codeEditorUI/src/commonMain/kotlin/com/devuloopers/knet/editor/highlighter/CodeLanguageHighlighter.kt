package com.devuloopers.knet.editor.highlighter

import androidx.compose.ui.text.AnnotatedString

/**
 * Strategy interface for syntax highlighting and code folding across various document languages
 * (JSON, HTML, XML, PLAIN TEXT, etc.).
 */
interface CodeLanguageHighlighter {
    /**
     * Unique identifier for this language highlighter (e.g., "json", "html", "xml", "plain").
     */
    val languageId: String

    /**
     * Calculates line fold ranges matching opening and closing structures (brackets, tags).
     *
     * @param lines List of document lines split by newlines.
     * @return Map of `startLineIndex -> endLineIndex`.
     */
    fun calculateFoldRanges(lines: List<String>): Map<Int, Int>

    /**
     * Resolves matching closing symbol representation when a block is collapsed (e.g. `... }`, `... ]`, `... </HTML>`).
     */
    fun resolveClosingSymbol(lines: List<String>, endLineIndex: Int): String

    /**
     * Highlights a single line of text into a styled AnnotatedString.
     */
    fun highlightLine(lineText: String): AnnotatedString
}
