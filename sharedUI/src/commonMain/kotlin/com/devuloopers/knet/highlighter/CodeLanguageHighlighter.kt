package com.devuloopers.knet.highlighter

import androidx.compose.runtime.Composable

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
     * Renders the syntax-highlighted content for a given line.
     *
     * @param lineNumber 1-based line number.
     * @param lineText Raw line string content.
     * @param isFoldable True if this line starts a foldable block.
     * @param isCollapsed True if this line's block is currently collapsed.
     * @param closingSymbol Resolved closing symbol text.
     * @param onToggleFold Callback to expand/collapse block range.
     */
    @Composable
    fun RenderLineContent(
        lineNumber: Int,
        lineText: String,
        isFoldable: Boolean,
        isCollapsed: Boolean,
        closingSymbol: String,
        onToggleFold: () -> Unit
    )
}
